#include <freerdp/config.h>

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <winpr/crt.h>
#include <winpr/synch.h>
#include <winpr/thread.h>
#include <winpr/wlog.h>
#include <winpr/crypto.h>
#include <winpr/string.h>
#include <winpr/sysinfo.h>

#include <freerdp/freerdp.h>
#include <freerdp/client.h>
#include <freerdp/channels/channels.h>
#include <freerdp/channels/disp.h>
#include <freerdp/channels/cliprdr.h>
#include <freerdp/client/disp.h>
#include <freerdp/client/cliprdr.h>
#include <freerdp/client/channels.h>
#include <freerdp/client/rdpgfx.h>
#include <freerdp/channels/rdpgfx.h>
#include <freerdp/gdi/gfx.h>
#include <freerdp/gdi/gdi.h>
#include <freerdp/codec/region.h>
#include <freerdp/settings.h>
#include <freerdp/input.h>
#include <freerdp/update.h>
#include <freerdp/scancode.h>
#include <freerdp/error.h>
#include <freerdp/graphics.h>
#include <freerdp/event.h>

#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#define TAG "RemoteHubRDP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

enum
{
	RH_STATE_CONNECTING = 1,
	RH_STATE_CONNECTED = 2,
	RH_STATE_RECONNECTING = 3,
	RH_STATE_DISCONNECTED = 4,
	RH_STATE_FAILED = 5,
	RH_STATE_CERT_CHANGED = 6
};

typedef struct
{
	rdpClientContext common;
	JavaVM* vm;
	jobject callback;
	DispClientContext* disp;
	CliprdrClientContext* cliprdr;
	pthread_mutex_t lock;
	uint8_t* pixels;
	UINT32 width;
	UINT32 height;
	UINT32 stride;
	size_t capacity;
	atomic_int dirty;
	atomic_int connected;
	atomic_int stopRequested;
	atomic_ullong frames;
	UINT32 lastError;
	UINT64 startedAt;
	char fingerprint[192];
	char pendingText[8192];
	size_t pendingTextLen;
	atomic_int hasClipboardText;
} rhContext;

/* WinPR writes its diagnostics to stderr, which does not exist in an Android
 * app process. Capture them so a codec or channel failure is visible in the
 * in-app log rather than silently lost. */
#define RH_LOG_SLOTS 128
#define RH_LOG_LINE 512
static char g_engineLog[RH_LOG_SLOTS][RH_LOG_LINE];
static int g_logHead = 0;
static int g_logCount = 0;
static pthread_mutex_t g_logLock = PTHREAD_MUTEX_INITIALIZER;
static atomic_int g_logInstalled = 0;

static void rh_engine_log_push(DWORD level, const char* text)
{
	if (!text || !*text)
		return;
	char tag;
	switch (level)
	{
		case WLOG_FATAL:
		case WLOG_ERROR:
			tag = 'E';
			break;
		case WLOG_WARN:
			tag = 'W';
			break;
		case WLOG_INFO:
			tag = 'I';
			break;
		default:
			tag = 'D';
			break;
	}
	pthread_mutex_lock(&g_logLock);
	char* slot = g_engineLog[g_logHead];
	snprintf(slot, RH_LOG_LINE, "%c %s", tag, text);
	g_logHead = (g_logHead + 1) % RH_LOG_SLOTS;
	if (g_logCount < RH_LOG_SLOTS)
		g_logCount++;
	pthread_mutex_unlock(&g_logLock);
}

static BOOL rh_wlog_message(const wLogMessage* msg)
{
	if (!msg || !msg->TextString)
		return FALSE;
	char line[RH_LOG_LINE];
	const char* prefix = msg->PrefixString ? msg->PrefixString : "";
	snprintf(line, sizeof(line), "%s%s", prefix, msg->TextString);
	/* Trim the trailing newline WinPR adds; the app log adds its own layout. */
	size_t len = strlen(line);
	while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r'))
		line[--len] = '\0';
	rh_engine_log_push(msg->Level, line);
	__android_log_print(ANDROID_LOG_DEBUG, "RemoteHubEngine", "%s", line);
	return TRUE;
}

static void rh_install_engine_log(void)
{
	if (atomic_exchange(&g_logInstalled, 1))
		return;
	wLog* root = WLog_GetRoot();
	if (!root)
		return;
	if (!WLog_SetLogAppenderType(root, WLOG_APPENDER_CALLBACK))
		return;
	wLogAppender* appender = WLog_GetLogAppender(root);
	if (!appender)
		return;
	wLogCallbacks callbacks = { 0 };
	callbacks.message = rh_wlog_message;
	if (!WLog_ConfigureAppender(appender, "callbacks", &callbacks))
		return;
	if (!WLog_OpenAppender(root))
		return;
	if (!WLog_SetLogLevel(root, WLOG_INFO))
		LOGW("could not raise the engine log level");
	LOGI("engine log attached to the app diagnostics");
}

static JavaVM* g_vm = NULL;
static rhContext* g_ctx = NULL;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

static JNIEnv* rh_attach(int* attached)
{
	JNIEnv* env = NULL;
	*attached = 0;
	if (!g_vm)
		return NULL;
	if ((*g_vm)->GetEnv(g_vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK)
		return env;
	if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) == JNI_OK)
	{
		*attached = 1;
		return env;
	}
	return NULL;
}

static void rh_detach(int attached)
{
	if (attached && g_vm)
		(*g_vm)->DetachCurrentThread(g_vm);
}

static void rh_notify_state(rhContext* rh, jint state, const char* message)
{
	int attached = 0;
	JNIEnv* env = rh_attach(&attached);
	if (!env || !rh->callback)
	{
		rh_detach(attached);
		return;
	}
	jclass cls = (*env)->GetObjectClass(env, rh->callback);
	jmethodID mid = (*env)->GetMethodID(env, cls, "onState", "(ILjava/lang/String;)V");
	if (mid)
	{
		jstring jmsg = (*env)->NewStringUTF(env, message ? message : "");
		(*env)->CallVoidMethod(env, rh->callback, mid, state, jmsg);
		(*env)->DeleteLocalRef(env, jmsg);
	}
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionClear(env);
	(*env)->DeleteLocalRef(env, cls);
	rh_detach(attached);
}

static void rh_notify_resize(rhContext* rh, UINT32 w, UINT32 h)
{
	int attached = 0;
	JNIEnv* env = rh_attach(&attached);
	if (!env || !rh->callback)
	{
		rh_detach(attached);
		return;
	}
	jclass cls = (*env)->GetObjectClass(env, rh->callback);
	jmethodID mid = (*env)->GetMethodID(env, cls, "onResize", "(II)V");
	if (mid)
		(*env)->CallVoidMethod(env, rh->callback, mid, (jint)w, (jint)h);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionClear(env);
	(*env)->DeleteLocalRef(env, cls);
	rh_detach(attached);
}

static void rh_notify_clipboard(rhContext* rh, const char* text, jsize len)
{
	int attached = 0;
	JNIEnv* env = rh_attach(&attached);
	if (!env || !rh->callback)
	{
		rh_detach(attached);
		return;
	}
	jclass cls = (*env)->GetObjectClass(env, rh->callback);
	jmethodID mid = (*env)->GetMethodID(env, cls, "onClipboard", "([B)V");
	if (mid)
	{
		jbyteArray arr = (*env)->NewByteArray(env, len);
		if (arr)
		{
			(*env)->SetByteArrayRegion(env, arr, 0, len, (const jbyte*)text);
			(*env)->CallVoidMethod(env, rh->callback, mid, arr);
			(*env)->DeleteLocalRef(env, arr);
		}
	}
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionClear(env);
	(*env)->DeleteLocalRef(env, cls);
	rh_detach(attached);
}

static rhContext* rh_from_context(rdpContext* context)
{
	return (rhContext*)context;
}

/* Copy the GDI framebuffer into our tightly packed RGBA buffer, growing it when
 * the host resolution changes. The caller holds rh->lock. Source and destination
 * strides are kept independent: GDI aligns its rows, Android bitmaps do not. */
static void rh_blit_locked(rhContext* rh, rdpGdi* gdi)
{
	if (!gdi || !gdi->primary_buffer || gdi->width <= 0 || gdi->height <= 0)
		return;

	const UINT32 w = (UINT32)gdi->width;
	const UINT32 h = (UINT32)gdi->height;
	const size_t needed = (size_t)w * (size_t)h * 4;

	if (!rh->pixels || rh->capacity < needed)
	{
		uint8_t* grown = (uint8_t*)realloc(rh->pixels, needed);
		if (!grown)
			return;
		memset(grown, 0, needed);
		rh->pixels = grown;
		rh->capacity = needed;
	}

	const size_t dstStride = (size_t)w * 4;
	const size_t srcStride = (size_t)gdi->stride;
	const uint8_t* src = gdi->primary_buffer;
	for (UINT32 y = 0; y < h; y++)
		memcpy(rh->pixels + (size_t)y * dstStride, src + (size_t)y * srcStride, dstStride);

	rh->width = w;
	rh->height = h;
	rh->stride = (UINT32)dstStride;
}

static BOOL rh_begin_paint(rdpContext* context)
{
	rdpGdi* gdi = context->gdi;
	if (!gdi || !gdi->primary || !gdi->primary->hdc || !gdi->primary->hdc->hwnd)
		return FALSE;
	HGDI_WND hwnd = gdi->primary->hdc->hwnd;
	hwnd->invalid->null = TRUE;
	hwnd->ninvalid = 0;
	return TRUE;
}

static BOOL rh_end_paint(rdpContext* context)
{
	rhContext* rh = rh_from_context(context);
	rdpGdi* gdi = context->gdi;
	if (!gdi || !gdi->primary || !gdi->primary->hdc || !gdi->primary->hdc->hwnd)
		return FALSE;

	HGDI_WND hwnd = gdi->primary->hdc->hwnd;
	if (hwnd->invalid->null)
		return TRUE;

	pthread_mutex_lock(&rh->lock);
	rh_blit_locked(rh, gdi);
	pthread_mutex_unlock(&rh->lock);

	atomic_fetch_add(&rh->frames, 1);
	atomic_store(&rh->dirty, 1);
	hwnd->invalid->null = TRUE;
	return TRUE;
}

static BOOL rh_desktop_resize(rdpContext* context)
{
	rhContext* rh = rh_from_context(context);
	rdpGdi* gdi = context->gdi;
	if (!gdi)
		return FALSE;

	/* The host changed resolution. GDI must reallocate its framebuffer before
	 * anything else paints, otherwise every surface update is clipped against the
	 * old size and the screen stays empty for the rest of the session. */
	const UINT32 width = freerdp_settings_get_uint32(context->settings, FreeRDP_DesktopWidth);
	const UINT32 height = freerdp_settings_get_uint32(context->settings, FreeRDP_DesktopHeight);
	if (width > 0 && height > 0 && !gdi_resize(gdi, width, height))
	{
		LOGE("gdi_resize to %ux%u failed", width, height);
		return FALSE;
	}

	pthread_mutex_lock(&rh->lock);
	rh_blit_locked(rh, gdi);
	pthread_mutex_unlock(&rh->lock);
	atomic_store(&rh->dirty, 1);
	LOGI("desktop resized to %ux%u", gdi->width, gdi->height);
	rh_notify_resize(rh, gdi->width, gdi->height);
	return TRUE;
}

static UINT rh_cliprdr_monitor_ready(CliprdrClientContext* clip,
                                     const CLIPRDR_MONITOR_READY* ready)
{
	CLIPRDR_GENERAL_CAPABILITY_SET cap = { 0 };
	cap.capabilitySetType = CB_CAPSTYPE_GENERAL;
	cap.capabilitySetLength = CB_CAPSTYPE_GENERAL_LEN;
	cap.version = CB_CAPS_VERSION_2;
	cap.generalFlags = CB_USE_LONG_FORMAT_NAMES;

	CLIPRDR_CAPABILITIES caps = { 0 };
	ZeroMemory(&caps.common, sizeof(caps.common));
	caps.cCapabilitiesSets = 1;
	caps.capabilitySets = (CLIPRDR_CAPABILITY_SET*)&cap;
	return clip->ClientCapabilities(clip, &caps);
}

static UINT rh_cliprdr_format_list(CliprdrClientContext* clip, const CLIPRDR_FORMAT_LIST* list)
{
	BOOL textAvailable = FALSE;
	for (UINT32 i = 0; i < list->numFormats; i++)
	{
		if (list->formats[i].formatId == CF_UNICODETEXT || list->formats[i].formatId == CF_TEXT)
			textAvailable = TRUE;
	}

	CLIPRDR_FORMAT_LIST_RESPONSE response = { 0 };
	response.common.msgType = CB_FORMAT_LIST_RESPONSE;
	response.common.msgFlags = CB_RESPONSE_OK;
	response.common.dataLen = 0;
	(void)clip->ClientFormatListResponse(clip, &response);

	if (!textAvailable)
		return CHANNEL_RC_OK;

	CLIPRDR_FORMAT_DATA_REQUEST request = { 0 };
	request.requestedFormatId = CF_UNICODETEXT;
	return clip->ClientFormatDataRequest(clip, &request);
}

static UINT rh_cliprdr_format_data_response(CliprdrClientContext* clip,
                                            const CLIPRDR_FORMAT_DATA_RESPONSE* response)
{
	rhContext* rh = (rhContext*)clip->custom;
	if (response->common.msgFlags != CB_RESPONSE_OK || !response->requestedFormatData)
		return CHANNEL_RC_OK;

	const BYTE* data = response->requestedFormatData;
	UINT32 len = response->common.dataLen;
	if (len >= 2)
	{
		size_t wideChars = len / sizeof(WCHAR);
		WCHAR* wide = (WCHAR*)malloc((wideChars + 1) * sizeof(WCHAR));
		if (wide)
		{
			memcpy(wide, data, wideChars * sizeof(WCHAR));
			wide[wideChars] = 0;
			size_t utf8Len = 0;
			char* utf8 = ConvertWCharToUtf8Alloc(wide, &utf8Len);
			if (utf8 && utf8Len > 0)
				rh_notify_clipboard(rh, utf8, (jsize)utf8Len);
			free(utf8);
			free(wide);
		}
	}

	CLIPRDR_FORMAT_DATA_RESPONSE ack = { 0 };
	ack.common.msgType = CB_FORMAT_DATA_RESPONSE;
	ack.common.msgFlags = CB_RESPONSE_OK;
	ack.common.dataLen = 0;
	ack.requestedFormatData = NULL;
	return CHANNEL_RC_OK;
}

static UINT rh_cliprdr_format_data_request(CliprdrClientContext* clip,
                                           const CLIPRDR_FORMAT_DATA_REQUEST* request)
{
	rhContext* rh = (rhContext*)clip->custom;
	CLIPRDR_FORMAT_DATA_RESPONSE response = { 0 };
	response.common.msgType = CB_FORMAT_DATA_RESPONSE;

	pthread_mutex_lock(&rh->lock);
	if (!atomic_load(&rh->hasClipboardText) || rh->pendingTextLen == 0)
	{
		pthread_mutex_unlock(&rh->lock);
		response.common.msgFlags = CB_RESPONSE_FAIL;
		response.common.dataLen = 0;
		response.requestedFormatData = NULL;
		return clip->ClientFormatDataResponse(clip, &response);
	}

	size_t wideChars = 0;
	WCHAR* wide = ConvertUtf8ToWCharAlloc(rh->pendingText, &wideChars);
	pthread_mutex_unlock(&rh->lock);

	if (!wide || wideChars == 0)
	{
		free(wide);
		response.common.msgFlags = CB_RESPONSE_FAIL;
		response.common.dataLen = 0;
		response.requestedFormatData = NULL;
		return clip->ClientFormatDataResponse(clip, &response);
	}

	response.common.msgFlags = CB_RESPONSE_OK;
	response.common.dataLen = (UINT32)wideChars * sizeof(WCHAR);
	response.requestedFormatData = (const BYTE*)wide;
	UINT rc = clip->ClientFormatDataResponse(clip, &response);
	free(wide);
	return rc;
}

static void rh_channel_connected(void* ctx, const ChannelConnectedEventArgs* e)
{
	rdpContext* context = (rdpContext*)ctx;
	rhContext* rh = rh_from_context(context);
	if (!e || !e->name)
		return;

	/* FreeRDP delegates the graphics-pipeline hookup to the client: this is the
	 * call that runs gdi_graphics_pipeline_init(gdi, gfx). Without it the RDPGFX
	 * channel connects but no surface command ever reaches the framebuffer, so
	 * EndPaint never fires and the desktop stays at GDI's initial 0xff fill.
	 * The handler ignores DISP and CLIPRDR, which are set up below. */
	freerdp_client_OnChannelConnectedEventHandler(context, e);

	if (strcmp(e->name, RDPGFX_DVC_CHANNEL_NAME) == 0)
	{
		LOGI("graphics pipeline attached to GDI");
	}
	else if (strcmp(e->name, DISP_DVC_CHANNEL_NAME) == 0)
	{
		rh->disp = (DispClientContext*)e->pInterface;
		if (rh->disp)
			rh->disp->custom = rh;
		LOGI("display control channel attached");
	}
	else if (strcmp(e->name, CLIPRDR_SVC_CHANNEL_NAME) == 0)
	{
		CliprdrClientContext* clip = (CliprdrClientContext*)e->pInterface;
		rh->cliprdr = clip;
		if (clip)
		{
			clip->custom = rh;
			clip->MonitorReady = rh_cliprdr_monitor_ready;
			clip->ServerFormatList = rh_cliprdr_format_list;
			clip->ServerFormatDataResponse = rh_cliprdr_format_data_response;
			clip->ServerFormatDataRequest = rh_cliprdr_format_data_request;
		}
		LOGI("clipboard channel attached");
	}
}

static void rh_channel_disconnected(void* ctx, const ChannelDisconnectedEventArgs* e)
{
	rdpContext* context = (rdpContext*)ctx;
	rhContext* rh = rh_from_context(context);
	if (!e || !e->name)
		return;
	freerdp_client_OnChannelDisconnectedEventHandler(context, e);
	if (strcmp(e->name, DISP_DVC_CHANNEL_NAME) == 0)
		rh->disp = NULL;
	else if (strcmp(e->name, CLIPRDR_SVC_CHANNEL_NAME) == 0)
		rh->cliprdr = NULL;
}

static DWORD rh_verify_certificate_ex(freerdp* instance, const char* host, UINT16 port,
                                      const char* common_name, const char* subject,
                                      const char* issuer, const char* fingerprint, DWORD flags)
{
	rhContext* rh = rh_from_context(instance->context);
	rdpSettings* settings = instance->context->settings;
	const char* pinned = freerdp_settings_get_string(settings, FreeRDP_CertificateAcceptedFingerprints);

	pthread_mutex_lock(&rh->lock);
	snprintf(rh->fingerprint, sizeof(rh->fingerprint), "%s", fingerprint ? fingerprint : "");
	pthread_mutex_unlock(&rh->lock);

	if (pinned && *pinned && fingerprint)
	{
		/* Return 2 = accept for this session only. The app keeps the fingerprint in
		 * its own encrypted store, so FreeRDP never has to write a known_hosts file. */
		if (strstr(pinned, fingerprint) != NULL)
			return 2;
		LOGW("certificate fingerprint does not match the pinned value");
		rh_notify_state(rh, RH_STATE_CERT_CHANGED, "fingerprint_mismatch");
		return 0;
	}
	LOGI("trusting certificate on first use: %s", fingerprint ? fingerprint : "");
	return 2;
}

static DWORD rh_verify_changed_certificate_ex(freerdp* instance, const char* host, UINT16 port,
                                              const char* common_name, const char* subject,
                                              const char* issuer, const char* new_fingerprint,
                                              const char* old_subject, const char* old_issuer,
                                              const char* old_fingerprint, DWORD flags)
{
	rhContext* rh = rh_from_context(instance->context);
	pthread_mutex_lock(&rh->lock);
	snprintf(rh->fingerprint, sizeof(rh->fingerprint), "%s", new_fingerprint ? new_fingerprint : "");
	pthread_mutex_unlock(&rh->lock);
	rh_notify_state(rh, RH_STATE_CERT_CHANGED, "certificate_changed");
	return 0;
}

static BOOL rh_authenticate_ex(freerdp* instance, char** username, char** password, char** domain,
                               rdp_auth_reason reason)
{
	rdpSettings* settings = instance->context->settings;
	const char* user = freerdp_settings_get_string(settings, FreeRDP_Username);
	const char* pass = freerdp_settings_get_string(settings, FreeRDP_Password);
	const char* dom = freerdp_settings_get_string(settings, FreeRDP_Domain);
	if (!user || !pass)
		return FALSE;
	if (username && !*username)
		*username = _strdup(user);
	if (password && !*password)
		*password = _strdup(pass);
	if (domain && !*domain && dom)
		*domain = _strdup(dom);
	return TRUE;
}

static BOOL rh_pre_connect(freerdp* instance)
{
	rdpContext* context = instance->context;
	if (PubSub_SubscribeChannelConnected(context->pubSub, rh_channel_connected) != CHANNEL_RC_OK)
		return FALSE;
	if (PubSub_SubscribeChannelDisconnected(context->pubSub, rh_channel_disconnected) != CHANNEL_RC_OK)
		return FALSE;
	if (!freerdp_client_load_channels(instance))
		return FALSE;
	return TRUE;
}

static BOOL rh_post_connect(freerdp* instance)
{
	rhContext* rh = rh_from_context(instance->context);
	rdpUpdate* update = instance->context->update;

	/* RGBX32 matches Android's RGBA_8888 byte order, which is what nativeRender
	 * memcpy's into. BGRA32 would swap red and blue. */
	if (!gdi_init(instance, PIXEL_FORMAT_RGBX32))
		return FALSE;

	rdpGdi* gdi = instance->context->gdi;
	update->BeginPaint = rh_begin_paint;
	update->EndPaint = rh_end_paint;
	update->DesktopResize = rh_desktop_resize;

	pthread_mutex_lock(&rh->lock);
	rh_blit_locked(rh, gdi);
	pthread_mutex_unlock(&rh->lock);

	atomic_store(&rh->connected, 1);
	atomic_store(&rh->dirty, 1);
	rh->startedAt = GetTickCount64();
	rh_notify_resize(rh, gdi->width, gdi->height);
	rh_notify_state(rh, RH_STATE_CONNECTED, "connected");
	return TRUE;
}

static void rh_post_disconnect(freerdp* instance)
{
	rhContext* rh = rh_from_context(instance->context);
	atomic_store(&rh->connected, 0);
	gdi_free(instance);
	rh_notify_state(rh, RH_STATE_DISCONNECTED, "disconnected");
}

static void rh_post_final_disconnect(freerdp* instance)
{
	rdpContext* context = instance->context;
	PubSub_UnsubscribeChannelConnected(context->pubSub, rh_channel_connected);
	PubSub_UnsubscribeChannelDisconnected(context->pubSub, rh_channel_disconnected);
}

static BOOL rh_client_new(freerdp* instance, rdpContext* context)
{
	instance->PreConnect = rh_pre_connect;
	instance->PostConnect = rh_post_connect;
	instance->PostDisconnect = rh_post_disconnect;
	instance->PostFinalDisconnect = rh_post_final_disconnect;
	instance->AuthenticateEx = rh_authenticate_ex;
	instance->VerifyCertificateEx = rh_verify_certificate_ex;
	instance->VerifyChangedCertificateEx = rh_verify_changed_certificate_ex;
	instance->LogonErrorInfo = NULL;
	return TRUE;
}

static void rh_client_free(freerdp* instance, rdpContext* context)
{
}

static int rh_client_entry(RDP_CLIENT_ENTRY_POINTS* entry)
{
	ZeroMemory(entry, sizeof(RDP_CLIENT_ENTRY_POINTS));
	entry->Version = RDP_CLIENT_INTERFACE_VERSION;
	entry->Size = sizeof(RDP_CLIENT_ENTRY_POINTS_V1);
	entry->ContextSize = sizeof(rhContext);
	entry->ClientNew = rh_client_new;
	entry->ClientFree = rh_client_free;
	entry->ClientStart = NULL;
	entry->ClientStop = NULL;
	return 0;
}

static DWORD WINAPI rh_thread(LPVOID param)
{
	freerdp* instance = (freerdp*)param;
	rdpContext* context = instance->context;
	rhContext* rh = rh_from_context(context);
	DWORD status = CHANNEL_RC_OK;
	HANDLE handles[64];

	rh_notify_state(rh, RH_STATE_CONNECTING, "connecting");

	if (freerdp_client_start(context) != CHANNEL_RC_OK)
	{
		rh_notify_state(rh, RH_STATE_FAILED, "client_start_failed");
		ExitThread(1);
		return 1;
	}

	if (!freerdp_connect(instance))
	{
		rh->lastError = freerdp_get_last_error(context);
		char message[96];
		snprintf(message, sizeof(message), "connect_failed_0x%08X", (unsigned)rh->lastError);
		LOGE("%s", message);
		freerdp_client_stop(context);
		rh_notify_state(rh, RH_STATE_FAILED, message);
		ExitThread(1);
		return 1;
	}

	while (!atomic_load(&rh->stopRequested) && !freerdp_shall_disconnect_context(context))
	{
		DWORD count = freerdp_get_event_handles(context, handles, 64);
		if (count == 0)
		{
			status = GetLastError();
			break;
		}
		DWORD wait = WaitForMultipleObjects(count, handles, FALSE, 100);
		if (wait == WAIT_FAILED)
		{
			status = GetLastError();
			break;
		}
		if (!freerdp_check_event_handles(context))
		{
			if (atomic_load(&rh->stopRequested))
				break;
			if (freerdp_get_last_error(context) == FREERDP_ERROR_SUCCESS)
				continue;
			rh_notify_state(rh, RH_STATE_RECONNECTING, "reconnecting");
			if (!client_auto_reconnect(instance))
			{
				status = freerdp_get_last_error(context);
				break;
			}
			rh_notify_state(rh, RH_STATE_CONNECTED, "reconnected");
		}
	}

	freerdp_disconnect(instance);
	freerdp_client_stop(context);
	atomic_store(&rh->connected, 0);
	rh_notify_state(rh, RH_STATE_DISCONNECTED, "closed");
	ExitThread(status);
	return status;
}

JNIEXPORT jlong JNICALL Java_app_remotehub_rdp_NativeRdp_nativeCreate(JNIEnv* env, jclass cls,
                                                                     jobject callback)
{
	pthread_mutex_lock(&g_lock);
	if (g_ctx)
	{
		pthread_mutex_unlock(&g_lock);
		return 0;
	}
	(*env)->GetJavaVM(env, &g_vm);

	RDP_CLIENT_ENTRY_POINTS entry;
	if (rh_client_entry(&entry) != 0)
	{
		pthread_mutex_unlock(&g_lock);
		return 0;
	}
	rdpContext* context = freerdp_client_context_new(&entry);
	if (!context)
	{
		pthread_mutex_unlock(&g_lock);
		return 0;
	}
	rhContext* rh = rh_from_context(context);
	pthread_mutex_init(&rh->lock, NULL);
	rh->vm = g_vm;
	rh->callback = (*env)->NewGlobalRef(env, callback);
	atomic_init(&rh->dirty, 0);
	atomic_init(&rh->connected, 0);
	atomic_init(&rh->stopRequested, 0);
	atomic_init(&rh->frames, 0);
	atomic_init(&rh->hasClipboardText, 0);
	g_ctx = rh;
	pthread_mutex_unlock(&g_lock);
	return (jlong)(intptr_t)rh;
}

static void rh_set_string(rdpSettings* settings, FreeRDP_Settings_Keys_String key, jstring value,
                          JNIEnv* env)
{
	if (!value)
		return;
	const char* raw = (*env)->GetStringUTFChars(env, value, NULL);
	if (raw)
	{
		freerdp_settings_set_string(settings, key, raw);
		(*env)->ReleaseStringUTFChars(env, value, raw);
	}
}

JNIEXPORT jboolean JNICALL Java_app_remotehub_rdp_NativeRdp_nativeConnect(
    JNIEnv* env, jclass cls, jlong handle, jstring host, jint port, jstring user, jstring password,
    jstring domain, jint width, jint height, jint colorDepth, jboolean nla, jboolean gfx,
    jboolean h264, jboolean clipboard, jboolean audio, jstring pinnedFingerprint, jint quality)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh)
		return JNI_FALSE;
	rh_install_engine_log();
	rdpContext* context = &rh->common.context;
	rdpSettings* settings = context->settings;
	freerdp* instance = context->instance;

	rh_set_string(settings, FreeRDP_ServerHostname, host, env);
	freerdp_settings_set_uint32(settings, FreeRDP_ServerPort, (UINT32)port);
	rh_set_string(settings, FreeRDP_Username, user, env);
	rh_set_string(settings, FreeRDP_Password, password, env);
	rh_set_string(settings, FreeRDP_Domain, domain, env);
	rh_set_string(settings, FreeRDP_ClientHostname, user, env);

	freerdp_settings_set_uint32(settings, FreeRDP_DesktopWidth, (UINT32)width);
	freerdp_settings_set_uint32(settings, FreeRDP_DesktopHeight, (UINT32)height);
	freerdp_settings_set_uint32(settings, FreeRDP_ColorDepth, (UINT32)colorDepth);
	freerdp_settings_set_bool(settings, FreeRDP_SoftwareGdi, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_SupportGraphicsPipeline, gfx ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GfxH264, h264 ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GfxAVC444, h264 ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GfxProgressive, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GfxProgressiveV2, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GfxPlanar, TRUE);
	/* THINCLIENT advertises an H.264-only thin client and SMALL_CACHE shrinks the
	 * surface cache to 20 MB. Neither is needed here, and both push Windows towards
	 * codecs this build may not decode. Advertising a full cache lets the server use
	 * RemoteFX or planar, which libfreerdp decodes on its own. */
	freerdp_settings_set_bool(settings, FreeRDP_GfxThinClient, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GfxSmallCache, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_RemoteFxCodec, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_NSCodec, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_BitmapCacheEnabled, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_BitmapCacheV3Enabled, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_FastPathOutput, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_FastPathInput, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_FrameMarkerCommandEnabled, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_SurfaceFrameMarkerEnabled, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_SupportErrorInfoPdu, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_SupportStatusInfoPdu, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_AutoReconnectionEnabled, TRUE);
	freerdp_settings_set_uint32(settings, FreeRDP_AutoReconnectMaxRetries, 8);
	freerdp_settings_set_bool(settings, FreeRDP_TcpKeepAlive, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_NlaSecurity, nla ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_TlsSecurity, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_RdpSecurity, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_NegotiateSecurityLayer, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_RedirectClipboard, clipboard ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_AudioPlayback, audio ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_AudioCapture, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_DisableWallpaper, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_DisableFullWindowDrag, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_DisableMenuAnims, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_DisableThemes, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_DisableCursorShadow, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_DisableCursorBlinking, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_AllowDesktopComposition, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_AllowFontSmoothing, quality >= 2 ? TRUE : FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_CompressionEnabled, TRUE);
	freerdp_settings_set_uint32(settings, FreeRDP_CompressionLevel, 2);
	freerdp_settings_set_uint32(settings, FreeRDP_MultifragMaxRequestSize, 0xFFFF);
	freerdp_settings_set_bool(settings, FreeRDP_DynamicResolutionUpdate, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_SupportMonitorLayoutPdu, TRUE);
	freerdp_settings_set_uint32(settings, FreeRDP_KeyboardLayout, 0x409);
	freerdp_settings_set_uint32(settings, FreeRDP_KeyboardType, 4);
	freerdp_settings_set_uint32(settings, FreeRDP_KeyboardSubType, 0);
	freerdp_settings_set_uint32(settings, FreeRDP_KeyboardFunctionKey, 12);
	freerdp_settings_set_bool(settings, FreeRDP_UnicodeInput, TRUE);
	freerdp_settings_set_bool(settings, FreeRDP_SmartSizing, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_Workarea, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_GatewayEnabled, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_ConsoleSession, FALSE);
	/* Level 2 keeps certificate verification ON. In FreeRDP 3.33.1
	 * (libfreerdp/crypto/tls.c, tls_verify_certificate) level 0 short-circuits to
	 * "success" before any callback runs, which would silently disable both the
	 * pinned fingerprint check and trust-on-first-use below. */
	freerdp_settings_set_uint32(settings, FreeRDP_AuthenticationLevel, 2);
	freerdp_settings_set_bool(settings, FreeRDP_IgnoreCertificate, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_AutoAcceptCertificate, FALSE);
	freerdp_settings_set_bool(settings, FreeRDP_AutoDenyCertificate, FALSE);
	freerdp_settings_set_uint16(settings, FreeRDP_DesktopOrientation, 0);
	freerdp_settings_set_uint32(settings, FreeRDP_DesktopScaleFactor, 100);
	freerdp_settings_set_uint32(settings, FreeRDP_DeviceScaleFactor, 100);

	if (pinnedFingerprint)
	{
		const char* fp = (*env)->GetStringUTFChars(env, pinnedFingerprint, NULL);
		if (fp && *fp)
		{
			freerdp_settings_set_string(settings, FreeRDP_CertificateAcceptedFingerprints, fp);
			pthread_mutex_lock(&rh->lock);
			snprintf(rh->fingerprint, sizeof(rh->fingerprint), "%s", fp);
			pthread_mutex_unlock(&rh->lock);
		}
		if (fp)
			(*env)->ReleaseStringUTFChars(env, pinnedFingerprint, fp);
	}

	atomic_store(&rh->stopRequested, 0);
	rh->common.thread = CreateThread(NULL, 0, rh_thread, instance, 0, NULL);
	return rh->common.thread != NULL ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_app_remotehub_rdp_NativeRdp_nativeAllocFrame(JNIEnv* env, jclass cls,
                                                                           jlong handle, jint w,
                                                                           jint h)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || w <= 0 || h <= 0)
		return JNI_FALSE;
	const size_t needed = (size_t)w * (size_t)h * 4;
	pthread_mutex_lock(&rh->lock);
	uint8_t* buffer = (uint8_t*)realloc(rh->pixels, needed);
	if (!buffer)
	{
		pthread_mutex_unlock(&rh->lock);
		return JNI_FALSE;
	}
	rh->pixels = buffer;
	rh->capacity = needed;
	rh->width = (UINT32)w;
	rh->height = (UINT32)h;
	rh->stride = (UINT32)w * 4;
	memset(buffer, 0, needed);
	pthread_mutex_unlock(&rh->lock);
	atomic_store(&rh->dirty, 1);
	return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_app_remotehub_rdp_NativeRdp_nativeRender(JNIEnv* env, jclass cls,
                                                                   jlong handle, jobject bitmap)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !bitmap)
		return -1;
	if (!atomic_exchange(&rh->dirty, 0))
		return 0;

	AndroidBitmapInfo info;
	if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
		return -1;
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
		return -1;

	void* dst = NULL;
	if (AndroidBitmap_lockPixels(env, bitmap, &dst) != ANDROID_BITMAP_RESULT_SUCCESS)
		return -1;

	pthread_mutex_lock(&rh->lock);
	UINT32 w = rh->width < info.width ? rh->width : info.width;
	UINT32 h = rh->height < info.height ? rh->height : info.height;
	if (rh->pixels && dst && w && h)
	{
		size_t rowBytes = (size_t)w * 4;
		for (UINT32 y = 0; y < h; y++)
		{
			memcpy((uint8_t*)dst + (size_t)y * info.stride, rh->pixels + (size_t)y * rh->stride,
			       rowBytes);
		}
	}
	pthread_mutex_unlock(&rh->lock);

	AndroidBitmap_unlockPixels(env, bitmap);
	return 1;
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeMouse(JNIEnv* env, jclass cls,
                                                                  jlong handle, jint flags, jint x,
                                                                  jint y)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected))
		return;
	freerdp_input_send_mouse_event(rh->common.context.input, (UINT16)flags, (UINT16)x, (UINT16)y);
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeWheel(JNIEnv* env, jclass cls,
                                                                  jlong handle, jint flags, jint x,
                                                                  jint y)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected))
		return;
	freerdp_input_send_extended_mouse_event(rh->common.context.input, (UINT16)flags, (UINT16)x,
	                                        (UINT16)y);
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeTouch(JNIEnv* env, jclass cls,
                                                                  jlong handle, jint flags,
                                                                  jint finger, jint pressure,
                                                                  jint x, jint y)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected))
		return;
	freerdp_client_handle_touch(&rh->common, (UINT32)flags, (INT32)finger, (UINT32)pressure,
	                            (INT32)x, (INT32)y);
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeKey(JNIEnv* env, jclass cls,
                                                                jlong handle, jint scancode,
                                                                jboolean down)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected))
		return;
	freerdp_input_send_keyboard_event_ex(rh->common.context.input, down ? TRUE : FALSE, FALSE,
	                                     (UINT32)scancode);
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeUnicode(JNIEnv* env, jclass cls,
                                                                    jlong handle, jint code)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected))
		return;
	freerdp_input_send_unicode_keyboard_event(rh->common.context.input, 0, (UINT16)code);
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeSync(JNIEnv* env, jclass cls,
                                                                 jlong handle, jint flags)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected))
		return;
	freerdp_input_send_synchronize_event(rh->common.context.input, (UINT32)flags);
}

JNIEXPORT jboolean JNICALL Java_app_remotehub_rdp_NativeRdp_nativeResize(JNIEnv* env, jclass cls,
                                                                       jlong handle, jint w,
                                                                       jint h)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected) || w < 200 || h < 200)
		return JNI_FALSE;
	rdpSettings* settings = rh->common.context.settings;
	freerdp_settings_set_uint32(settings, FreeRDP_DesktopWidth, (UINT32)w);
	freerdp_settings_set_uint32(settings, FreeRDP_DesktopHeight, (UINT32)h);

	DispClientContext* disp = rh->disp;
	if (!disp || !disp->SendMonitorLayout)
		return JNI_FALSE;

	DISPLAY_CONTROL_MONITOR_LAYOUT layout;
	ZeroMemory(&layout, sizeof(layout));
	layout.Flags = DISPLAY_CONTROL_MONITOR_PRIMARY;
	layout.Width = (UINT32)w;
	layout.Height = (UINT32)h;
	layout.Orientation = freerdp_settings_get_uint16(settings, FreeRDP_DesktopOrientation);
	layout.DesktopScaleFactor = freerdp_settings_get_uint32(settings, FreeRDP_DesktopScaleFactor);
	layout.DeviceScaleFactor = freerdp_settings_get_uint32(settings, FreeRDP_DeviceScaleFactor);
	return disp->SendMonitorLayout(disp, 1, &layout) == CHANNEL_RC_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_app_remotehub_rdp_NativeRdp_nativeSendClipboard(JNIEnv* env,
                                                                               jclass cls,
                                                                               jlong handle,
                                                                               jstring text)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !atomic_load(&rh->connected) || !text)
		return JNI_FALSE;
	const char* raw = (*env)->GetStringUTFChars(env, text, NULL);
	if (!raw)
		return JNI_FALSE;

	pthread_mutex_lock(&rh->lock);
	snprintf(rh->pendingText, sizeof(rh->pendingText), "%s", raw);
	rh->pendingTextLen = strlen(rh->pendingText);
	atomic_store(&rh->hasClipboardText, 1);
	pthread_mutex_unlock(&rh->lock);
	(*env)->ReleaseStringUTFChars(env, text, raw);

	CliprdrClientContext* clip = rh->cliprdr;
	if (!clip || !clip->ClientFormatList)
		return JNI_FALSE;

	CLIPRDR_FORMAT formats[2];
	formats[0].formatId = CF_UNICODETEXT;
	formats[0].formatName = NULL;
	formats[1].formatId = CF_TEXT;
	formats[1].formatName = NULL;

	CLIPRDR_FORMAT_LIST list;
	ZeroMemory(&list, sizeof(list));
	list.common.msgType = CB_FORMAT_LIST;
	list.common.msgFlags = CB_ASCII_NAMES;
	list.common.dataLen = 0;
	list.numFormats = 2;
	list.formats = formats;
	return clip->ClientFormatList(clip, &list) == CHANNEL_RC_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_app_remotehub_rdp_NativeRdp_nativeFrames(JNIEnv* env, jclass cls,
                                                                    jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	return rh ? (jlong)atomic_load(&rh->frames) : 0;
}

JNIEXPORT jint JNICALL Java_app_remotehub_rdp_NativeRdp_nativeWidth(JNIEnv* env, jclass cls,
                                                                  jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	return rh ? (jint)rh->width : 0;
}

JNIEXPORT jint JNICALL Java_app_remotehub_rdp_NativeRdp_nativeHeight(JNIEnv* env, jclass cls,
                                                                   jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	return rh ? (jint)rh->height : 0;
}

JNIEXPORT jboolean JNICALL Java_app_remotehub_rdp_NativeRdp_nativeConnected(JNIEnv* env, jclass cls,
                                                                          jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	return rh && atomic_load(&rh->connected) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_app_remotehub_rdp_NativeRdp_nativeLastError(JNIEnv* env, jclass cls,
                                                                      jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	return rh ? (jint)rh->lastError : 0;
}

JNIEXPORT jstring JNICALL Java_app_remotehub_rdp_NativeRdp_nativeFingerprint(JNIEnv* env, jclass cls,
                                                                           jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh)
		return (*env)->NewStringUTF(env, "");
	pthread_mutex_lock(&rh->lock);
	jstring out = (*env)->NewStringUTF(env, rh->fingerprint);
	pthread_mutex_unlock(&rh->lock);
	return out;
}

JNIEXPORT jlong JNICALL Java_app_remotehub_rdp_NativeRdp_nativeUptime(JNIEnv* env, jclass cls,
                                                                    jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh || !rh->startedAt)
		return 0;
	return (jlong)(GetTickCount64() - rh->startedAt);
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeDisconnect(JNIEnv* env, jclass cls,
                                                                       jlong handle)
{
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh)
		return;
	atomic_store(&rh->stopRequested, 1);
	freerdp_abort_connect_context(&rh->common.context);
	if (rh->common.thread)
	{
		WaitForSingleObject(rh->common.thread, 10000);
		CloseHandle(rh->common.thread);
		rh->common.thread = NULL;
	}
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeDestroy(JNIEnv* env, jclass cls,
                                                                    jlong handle)
{
	pthread_mutex_lock(&g_lock);
	rhContext* rh = (rhContext*)(intptr_t)handle;
	if (!rh)
	{
		pthread_mutex_unlock(&g_lock);
		return;
	}
	atomic_store(&rh->stopRequested, 1);
	freerdp_abort_connect_context(&rh->common.context);
	if (rh->common.thread)
	{
		WaitForSingleObject(rh->common.thread, 10000);
		CloseHandle(rh->common.thread);
		rh->common.thread = NULL;
	}
	if (rh->callback)
		(*env)->DeleteGlobalRef(env, rh->callback);
	free(rh->pixels);
	rh->pixels = NULL;
	pthread_mutex_destroy(&rh->lock);
	freerdp_client_context_free(&rh->common.context);
	if (g_ctx == rh)
		g_ctx = NULL;
	pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jstring JNICALL Java_app_remotehub_rdp_NativeRdp_nativeVersion(JNIEnv* env, jclass cls)
{
	return (*env)->NewStringUTF(env, freerdp_get_version_string());
}

JNIEXPORT void JNICALL Java_app_remotehub_rdp_NativeRdp_nativeSetLogLevel(JNIEnv* env, jclass cls,
                                                                        jint level)
{
	rh_install_engine_log();
	if (!WLog_SetLogLevel(WLog_GetRoot(), (DWORD)level))
		LOGW("could not set the engine log level");
}

/* Hand the captured engine log to the app. Returns "" when there is nothing new. */
JNIEXPORT jstring JNICALL Java_app_remotehub_rdp_NativeRdp_nativeDrainEngineLog(JNIEnv* env,
                                                                              jclass cls)
{
	char joined[RH_LOG_SLOTS * RH_LOG_LINE];
	size_t used = 0;
	joined[0] = '\0';

	pthread_mutex_lock(&g_logLock);
	const int count = g_logCount;
	const int start = (g_logHead - count + RH_LOG_SLOTS) % RH_LOG_SLOTS;
	for (int i = 0; i < count; i++)
	{
		const char* line = g_engineLog[(start + i) % RH_LOG_SLOTS];
		size_t len = strlen(line);
		if (used + len + 2 >= sizeof(joined))
			break;
		memcpy(joined + used, line, len);
		used += len;
		joined[used++] = '\n';
		joined[used] = '\0';
	}
	g_logCount = 0;
	g_logHead = 0;
	pthread_mutex_unlock(&g_logLock);

	return (*env)->NewStringUTF(env, joined);
}
