package com.mrrwhoo1.multidl

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

object FfmpegBridge {
    @JvmStatic
    fun execute(command: String): String {
        val session = FFmpegKit.execute(command)
        val output = session.output ?: ""
        val code = session.returnCode

        return if (ReturnCode.isSuccess(code)) {
            "SUCCESS\n$output"
        } else {
            "FAILED: ${session.failStackTrace ?: output}"
        }
    }
}
