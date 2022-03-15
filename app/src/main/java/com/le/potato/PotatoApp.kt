package com.le.potato

import android.app.Application
import android.content.Context
import org.acra.config.mailSender
import org.acra.config.dialog
import org.acra.data.StringFormat
import org.acra.ktx.initAcra


class PotatoApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initAcra {
            //core configuration:
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            // 1000 lines of logs with date, invocation time, priority/tag, and PID of the originating process.
            logcatArguments = arrayOf("-t", "1000", "-v", "time")
            dialog {
                text = getString(R.string.crash_dialog_text)
                title = getString(R.string.crash)
            }
            mailSender {
                mailTo = "elpotatoapp+crashreport@gmail.com"
                subject = "el potato crash report"
                body = "See attached report file with error details"
            }
        }
    }
}