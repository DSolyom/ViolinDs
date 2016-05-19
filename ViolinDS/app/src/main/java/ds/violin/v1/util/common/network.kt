/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.util.common

import android.content.Context
import android.net.http.HttpResponseCache
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * enable http response cache - call before any networking
 */
fun enableHttpResponseCache(context: Context) {
    try {
        val httpCacheSize = 10L * 1024L * 1024L
        val httpCacheDir = File(context.cacheDir, "http")
        HttpResponseCache.install(httpCacheDir, httpCacheSize)
    } catch (e: Throwable) {
        Debug.logW("Common", "HttpResponseCache unavailable")
    }
}

/**
 * disable ssl certificate checking - call before any networking
 */
fun disableSSLCertificateChecking() {
    var trustAllCerts = arrayOf<X509TrustManager>(NullX509TrustManager())
    try {
        var sc: SSLContext = SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, java.security.SecureRandom())

        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
    } catch (e: KeyManagementException) {
        e.printStackTrace();
    } catch (e: NoSuchAlgorithmException) {
        e.printStackTrace();
    }
}

internal class NullX509TrustManager : X509TrustManager {

    override fun getAcceptedIssuers(): Array<out X509Certificate>? {
        return arrayOf()
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

}
