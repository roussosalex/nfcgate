#ifndef __ANDROID__
#define __ANDROID__
#endif

#include <android/log.h>
#include <jni.h>
#include <stdint.h>
#include <cstdlib>

#include <nfcd/libnfc-external.h>
#include <nfcd/helper/Config.h>

#define LOG_TAG "NATIVENFC"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__ )
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__ )
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

inline void loghex(const char *desc, const uint8_t *data, const int len) {
    int strlen = len * 3 + 1;
    char *msg = (char *) malloc((size_t) strlen);
    for (uint8_t i = 0; i < len; i++) {
        sprintf(msg + i * 3, " %02x", (unsigned int) *(data + i));
    }
    LOGI("%s%s",desc, msg);
    free(msg);
}

// main.cpp
extern bool patchEnabled;

// chip.cpp
tNFC_STATUS hook_NfcSetConfig (UINT8 tlv_size, UINT8 *p_param_tlvs);
void hook_SetRfCback(tNFC_CONN_CBACK *p_cback);
tNFC_STATUS hook_NfcSenddata(UINT8 conn_id, BT_HDR *p_data);
tNFC_STATUS hook_NfcDeactivate (UINT8 deactivate_type);
tNFA_STATUS hook_NfaStopRfDiscovery();
tNFA_STATUS hook_NfaDisablePolling();
tNFA_STATUS hook_NfaStartRfDiscovery();
tNFA_STATUS hook_NfaEnablePolling(tNFA_TECHNOLOGY_MASK poll_mask);

void uploadPatchConfig();
void uploadOriginalConfig();
void enablePolling();
void disablePolling();

// define method signatures
typedef tNFC_STATUS NFC_SetConfig (UINT8     tlv_size, UINT8    *p_param_tlvs);
typedef tNFC_STATUS NFC_SendData(UINT8       conn_id,  BT_HDR     *p_data);
typedef tNFC_STATUS NFC_Deactivate (UINT8 deactivate_type);
typedef void NFC_SetStaticRfCback (tNFC_CONN_CBACK *p_cback);

typedef tNFA_STATUS NFA_StartRfDiscovery ();
typedef tNFA_STATUS NFA_EnablePolling (tNFA_TECHNOLOGY_MASK poll_mask);
typedef tNFA_STATUS NFA_StopRfDiscovery();
typedef tNFA_STATUS NFA_DisablePolling();

// external original methods
extern NFC_SetStaticRfCback *nci_orig_SetRfCback;
extern NFC_SetConfig *nci_orig_NfcSetConfig;
extern NFC_SendData *nfc_orig_sendData;
extern NFC_Deactivate *nfc_orig_deactivate;
extern NFA_StopRfDiscovery *nfa_orig_stop_rf_discovery;
extern NFA_DisablePolling *nfa_orig_disable_polling;
extern NFA_StartRfDiscovery *nfa_orig_start_rf_discovery;
extern NFA_EnablePolling *nfa_orig_enable_polling;

// hooks and symbols
extern tCE_CB *ce_cb;
extern Config patchValues;
extern struct hook_t hook_config;
extern struct hook_t hook_rfcback;
extern struct hook_t hook_senddata;
extern struct hook_t hook_deactivate;
extern struct hook_t hook_nfa_stop_rf_discovery;
extern struct hook_t hook_nfa_disable_polling;
extern struct hook_t hook_nfa_start_rf_discovery;
extern struct hook_t hook_nfa_enable_polling;
