package com.sandeep.readnfc

import android.app.Application
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import com.sandeep.readnfc.Constants.NFC_READER_DELAY
import com.sandeep.readnfc.NFCStatus.NoOperation
import com.sandeep.readnfc.NFCStatus.NotEnabled
import com.sandeep.readnfc.NFCStatus.NotSupported
import com.sandeep.readnfc.NFCStatus.Process
import com.sandeep.readnfc.NFCStatus.Read
import com.sandeep.readnfc.NFCStatus.Tap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MainViewModel"

class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private val prefix = "android.nfc.tech."
    private val CLA_INS_P1_P2 = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
    private val AID_ANDROID = byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06)

    private val liveNFC: MutableStateFlow<NFCStatus?> = MutableStateFlow(null)
    private val liveToast: MutableSharedFlow<String?> = MutableSharedFlow()
    private val liveTag: MutableStateFlow<String?> = MutableStateFlow(null)
    private val utils by lazy { Utils() }

    //region NFC Methods
    fun getNFCFlags(): Int {
        var flags = 0
        flags = flags or NfcAdapter.FLAG_READER_NFC_A
        flags = flags or NfcAdapter.FLAG_READER_NFC_B
        flags = flags or NfcAdapter.FLAG_READER_NFC_F
        flags = flags or NfcAdapter.FLAG_READER_NFC_V
        flags = flags or NfcAdapter.FLAG_READER_NFC_BARCODE
        // flags = flags or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        return flags
    }

    fun getExtras(): Bundle {
        return bundleOf(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY to NFC_READER_DELAY)
    }

    fun onCheckNFC(isChecked: Boolean) {
        Coroutines.io(this@MainViewModel) {
            if (isChecked) {
                Log.d(TAG, "onCheckNFC: true")
                postNFCStatus(Tap)
            } else {
                Log.d(TAG, "onCheckNFC: false")
                postNFCStatus(NoOperation)
                postToast("NFC is Disabled, Please Toggle On!")
            }
        }
    }

    fun readTag(tag: Tag?) {
        Coroutines.default(this@MainViewModel) {
            Log.d(TAG, "readTag: Tag $tag")
            Log.d(TAG, "readTag Tech List ${tag?.techList?.size}")
            postNFCStatus(Process)
            val stringBuilder: StringBuilder = StringBuilder()
            val id: ByteArray = tag?.id ?: byteArrayOf()
            Log.d(TAG, "readTag: byteToHex = ${bytesToHexString(id)}")
            stringBuilder.append("Tag ID (hex): ${utils.getHex(id)} \n")
            stringBuilder.append("Tag ID (dec): ${utils.getDec(id)} \n")
            stringBuilder.append("Tag ID (reversed): ${utils.getReversed(id)} \n")
            stringBuilder.append("Technologies: ${tag?.techList?.joinToString(", ") { it.substring(prefix.length) }}\n")
            tag?.techList?.forEach { tech ->
                when (tech) {
                    MifareClassic::class.java.name -> {
                        Log.d(TAG, "readTag: MifareClassic")
                        stringBuilder.append('\n')
                        val mifareTag: MifareClassic = MifareClassic.get(tag)
                        val type = when (mifareTag.type) {
                            MifareClassic.TYPE_CLASSIC -> "Classic"
                            MifareClassic.TYPE_PLUS -> "Plus"
                            MifareClassic.TYPE_PRO -> "Pro"
                            else -> "Unknown"
                        }
                        Log.d(TAG, "readTag: $type")
                        stringBuilder.append("Mifare Classic type: $type \n")
                        stringBuilder.append("Mifare size: ${mifareTag.size} bytes \n")
                        stringBuilder.append("Mifare sectors: ${mifareTag.sectorCount} \n")
                        stringBuilder.append("Mifare blocks: ${mifareTag.blockCount}")
                    }

                    MifareUltralight::class.java.name -> {
                        Log.d(TAG, "readTag: MifareUltralight")
                        stringBuilder.append('\n')
                        val mifareUlTag: MifareUltralight = MifareUltralight.get(tag)
                        val type = when (mifareUlTag.type) {
                            MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                            MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                            else -> "Unknown"
                        }
                        Log.d(TAG, "readTag: $type")
                        stringBuilder.append("Mifare Ultralight type: ")
                        stringBuilder.append(type)
                    }
                    IsoDep::class.java.name -> {
                        Log.d(TAG, "readTag: IsoDep")
                        val isoDep: IsoDep = IsoDep.get(tag)
                        isoDep.connect()
                        Log.d(TAG, "readTag: $isoDep")
                        if (isoDep.isConnected) {
                            val response: ByteArray =
                                isoDep.transceive(createSelectAidApdu(AID_ANDROID))
                            Log.d(TAG, "readTag: response byteToHex = ${bytesToHexString(response)}")
                        }
                        isoDep.close()
                    }
                }
            }
            Log.d(TAG, "dumpTagData Return \n $stringBuilder")
            postNFCStatus(Read)
            liveTag.emit("${utils.getDateTimeNow()} \n $stringBuilder")
        }
    }
    private fun createSelectAidApdu(aid: ByteArray): ByteArray {
        val result = ByteArray(6 + aid.size)
        System.arraycopy(
            CLA_INS_P1_P2,
            0,
            result,
            0,
            CLA_INS_P1_P2.size,
        )
        result[4] = aid.size.toByte()
        System.arraycopy(aid, 0, result, 5, aid.size)
        result[result.size - 1] = 0
        return result
    }

    private suspend fun postNFCStatus(status: NFCStatus) {
        Log.d(TAG, "postNFCStatus($status)")
        val application = application
        val nfcManager = NFCManager
        when {
            NFCManager.isSupportedAndEnabled(application) -> {
                Log.d(TAG, "isSupportedAndEnabled")
                liveNFC.emit(status)
                if (status == Tap) {
                    Log.d(TAG, "status = Tap")
                    liveTag.emit("Please Tap Now!")
                } else {
                    Log.d(TAG, "status = null")
                    liveTag.emit(null)
                }
            }

            NFCManager.isNotEnabled(application) -> {
                Log.d(TAG, "isNotEnabled")
                liveNFC.emit(NotEnabled)
                liveTag.emit("Please Enable your NFC!")
            }

            NFCManager.isNotSupported(application) -> {
                Log.d(TAG, "isNotSupported")
                liveNFC.emit(NotSupported)
                liveTag.emit("NFC Not Supported!")
            }
        }
    }

    fun observeNFCStatus(): StateFlow<NFCStatus?> {
        Log.d(TAG, "observeNFCStatus()")
        return liveNFC.asStateFlow()
    }

    fun observeTag(): StateFlow<String?> {
        Log.d(TAG, "observeTag()")
        return liveTag.asStateFlow()
    }
    //endregion

    //region Toast Methods
    private fun updateToast(message: String) {
        Coroutines.io(this@MainViewModel) {
            liveToast.emit(message)
        }
    }

    private suspend fun postToast(message: String) {
        Log.d(TAG, "postToast($message)")
        liveToast.emit(message)
    }

    fun observeToast(): SharedFlow<String?> {
        return liveToast.asSharedFlow()
    }

    private fun bytesToHexString(src: ByteArray): String? {
        if (utils.isNullOrEmpty(src)) {
            return null
        }
        val sb = StringBuilder()
        for (b in src) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
