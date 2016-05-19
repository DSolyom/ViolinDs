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

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.database.Cursor
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import ds.violin.v1.Global
import java.io.*
import java.text.Collator
import java.text.NumberFormat
import java.util.*

val SEND_EMAIL_ACTION_CODE = 21345

/**
 *
 * @param context
 * @param url
 */
fun openInBrowser(context: Context, url: String) {
    try {
        if (url.contains("mailto:")) {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            context.startActivity(intent);
            return;
        }
    } catch(e: Throwable) {
        Debug.logException(e);
    }
    val uri = createHttpUri(url);
    val intent = Intent(Intent.ACTION_VIEW, uri);
    context.startActivity(intent);
}

/**
 *
 * @param activity
 * @param url
 * @param requestCode
 */
fun openInBrowser(activity: Activity, url: String, requestCode: Int) {
    val uri = createHttpUri(url);
    val intent = Intent(Intent.ACTION_VIEW, uri);
    activity.startActivityForResult(intent, requestCode);
}

/**
 *
 * @param url
 * @return
 */
fun isDocument(url: String): Boolean {
    try {
        if (!url.contains("docs.google.com")) {
            val url = url.split("\\?")[0];
            if (url.endsWith(".pdf") || url.endsWith(".PDF") || url.endsWith(".doc") || url.endsWith(".DOC") ||
                    url.endsWith(".xls") || url.endsWith(".XLS")) {
                return true;
            }
        }
    } catch(e: Throwable) {
        ;
    }
    return false;
}

/**
 *
 * @param url
 * @return
 */
fun createHttpUri(url: String): Uri {
    var url = url
    if (url.length < 8 || !"http://".equals(url.substring(0, 7)) && !"https://".equals(url.substring(0, 8))) {
        url = "http://" + url;
    }
    return Uri.parse(url);
}

/**
 *
 * @param context
 * @param packageName
 * @param minVersionCode = -1
 * @return
 */
fun getAppOpenIntent(context: Context, packageName: String, minVersionCode: Int = -1): Intent? {
    val pm = context.getPackageManager();

    var intent = Intent("android.intent.action.MAIN");
    intent.addCategory("android.intent.category.LAUNCHER");
    for (ri in pm.queryIntentActivities(intent, 0)) {
        if (ri.activityInfo.packageName.equals(packageName)) {
            try {
                if (minVersionCode != -1 && pm.getPackageInfo(packageName, 0).versionCode < minVersionCode) {
                    return null
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // wont happen but still
                return null
            }

            // create intent
            intent = Intent("android.intent.action.MAIN");
            intent.setComponent(ComponentName(packageName, ri.activityInfo.name));
            intent.addCategory("android.intent.category.LAUNCHER");
            return intent;
        }
    }
    return null;
}

/**
 * open market at app's details screen
 *
 * @param context
 * @param packageName
 */
fun openMarket(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse("market://details?id=" + packageName)
    context.startActivity(intent)
}

/**
 * open market at app's details screen
 *
 * @param context
 * @param marketUrl
 * @param dummy
 */
fun openMarket(context: Context, marketUrl: String, dummy: Boolean) {
    val intent = Intent(Intent.ACTION_VIEW)

    intent.data = Uri.parse(marketUrl)
    context.startActivity(intent)
}

/**
 * open app or market at app's details screen if app not installed
 *
 * @param context
 * @param packageName
 * @param minVersion
 */
fun openAppOrMarket(context: Context, packageName: String, minVersion: Int) {
    val i = getAppOpenIntent(context, packageName, minVersion)
    if (i != null) {
        context.startActivity(i)
    } else {
        openMarket(context, packageName)
    }
}

/**
 * open app or market at app's details screen if app not installed
 *
 * @param context
 * @param packageName
 * @param minVersion
 */
fun openAppOrMarket(context: Context, i: Intent?, packageName: String, minVersion: Int) {
    if (i != null) {
        context.startActivity(i)
    } else {
        openMarket(context, packageName)
    }
}

/**
 * start phone call api
 *
 * @param context
 * @param phoneNumber
 */
fun startPhoneCallingDialog(context: Context, phoneNumber: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber)))
    } catch (e: Throwable) {
        Debug.logException(e)
    }

}

/**
 * start email - context must be an activity if you want to add attachments
 *
 * @param context
 * @param emails
 * @param subject
 * @param defaultMessage
 */
fun startSendingEmail(context: Context, emails: Array<String>,
                      subject: String, defaultMessage: String, vararg attachments: Uri) {
    val emailIntent = Intent(android.content.Intent.ACTION_SEND)

    emailIntent.setType("plain/text")
    emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, emails)
    emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, Html.fromHtml(subject).toString())
    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, Html.fromHtml(defaultMessage))

    if (attachments != null && attachments.size > 0) {
        for (attachment in attachments) {
            emailIntent.putExtra(android.content.Intent.EXTRA_STREAM, attachment)
        }
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        (context as Activity).startActivityForResult(Intent.createChooser(emailIntent, "Send mail..."), SEND_EMAIL_ACTION_CODE)
    } else {
        context.startActivity(Intent.createChooser(emailIntent, "Send mail..."))
    }
}

/**
 * to fix tile mode bug
 *
 * @param view
 */
fun fixTiledBackground(view: View) {
    try {
        fixTiledDrawable(view.background as BitmapDrawable)
    } catch (e: ClassCastException) {
    }

}

/**
 * to fix tile mode bug
 *
 * @param tiled
 */
fun fixTiledDrawable(tiled: BitmapDrawable?) {
    if (tiled == null) {
        return
    }
    tiled!!.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

/**
 * validate email
 *
 * @param string
 * @return
 */
fun checkEmail(string: String): Boolean {
    return string.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}$".toRegex())
}

/**
 * copy from assets
 *
 * @param context
 * @param assetDirOrFile
 * @param targetPath
 * @throws IOException
 */
@Throws(IOException::class)
fun copyFromAssets(context: Context, assetDirOrFile: String,
                   targetPath: String) {

    val assetManager = context.getAssets()
    var assets: Array<String>? = null

    assets = assetManager.list(assetDirOrFile)
    if (assets.size == 0) {
        copyFileFromAsset(assetManager, assetDirOrFile, targetPath)
    } else {
        if (targetPath.length > 0) {
            val fTargetPath = File(targetPath)
            if (!fTargetPath.exists()) {
                fTargetPath.mkdir()
            }
        }
        for (i in assets.indices) {
            copyFromAssets(context, assetDirOrFile + "/" + assets[i],
                    targetPath + "/" + assets[i])
        }
    }
}

@Throws(IOException::class)
fun createDirStructureFromAssets(context: Context, assetDir: String,
                                 targetPath: String?) {

    val assetManager = context.getAssets()
    var assets: Array<String>? = null

    assets = assetManager.list(assetDir)
    if (assets.size != 0) {
        if (targetPath != null && targetPath.length > 0) {
            val fTargetPath = File(targetPath)
            if (!fTargetPath.exists()) {
                fTargetPath.mkdir()
            }
        }
        for (i in assets.indices) {
            createDirStructureFromAssets(context, assetDir + "/" + assets[i],
                    targetPath + "/" + assets[i])
        }
    }
}

@Throws(IOException::class)
fun copyFileFromAsset(assetManager: AssetManager, assetFile: String,
                      targetFilePath: String) {

    var `in`: InputStream? = null
    var out: OutputStream? = null

    `in` = assetManager.open(assetFile)
    out = FileOutputStream(targetFilePath)

    val buffer = ByteArray(1024)
    var read: Int = `in`!!.read(buffer)
    while (read != -1) {
        out.write(buffer, 0, read)
        read = `in`.read(buffer)
    }
    `in`.close()
    out.flush()
    out.close()
}

fun deleteDirRec(dirPath: String) {
    val fDirPath = File(dirPath)
    if (!fDirPath.exists()) {
        return
    }
    val fileOrDirs = fDirPath.list()
    if (fileOrDirs.size > 0) {
        for (i in fileOrDirs.indices) {
            deleteDirRec(dirPath + "/" + fileOrDirs[i])
        }
    }
    fDirPath.delete()
}

/**
 * remove every view from a viewgroup
 *
 * @param vg
 */
@SuppressWarnings("unchecked", "rawtypes")
fun removeAllViewsRec(vg: ViewGroup) {
    val cnt = vg.getChildCount()
    for (i in 0..cnt - 1) {
        val child = vg.getChildAt(i)
        child.setBackground(null)
        if (child is ViewGroup) {
            removeAllViewsRec(child)
        } else if (child is ImageView) {
            child.setImageDrawable(null)
        }
        if (child is AdapterView<*>) {
            child.setAdapter(null)
        }
    }
    vg.removeAllViewsInLayout()
}

/**
 *
 * @param a
 * @param editResource
 */
fun showKeyboard(a: Activity, editResource: Int) {
    val edit = a.findViewById(editResource)
    val mgr = (a as Context).getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    mgr.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
}

/**
 *
 * @param a
 * @param editResource
 */
fun hideKeyboard(a: Activity, editResource: Int) {
    hideKeyboard(a, a.findViewById(editResource))
}

/**
 *
 * @param context
 * @param v
 */
fun hideKeyboard(context: Context, v: View) {
    try {
        val mgr = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        mgr.hideSoftInputFromWindow(v.getWindowToken(), 0)
    } catch (e: Exception) {
    }

}

/**
 * @param a
 */
fun hideKeyboard(a: Activity) {
    val im = a.getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    im.hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS)
}

/**
 * open soft keyboard
 *
 * @param context
 * @param view
 */
fun openKeyboard(context: Context, view: View) {
    val mgr = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    mgr.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * close soft keyboard
 *
 * @param context
 * @param view
 */
fun closeKeyboard(context: Context, view: View) {
    val mgr = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    mgr.hideSoftInputFromWindow(view.getWindowToken(), 0)
}

/**
 * @param number
 * @param currency
 * @return
 */
fun formatByCurrency(number: Float, currency: String): String {
    try {
        val cur = Currency.getInstance(currency)
        val format = NumberFormat.getCurrencyInstance(Global.context.resources.configuration.locale)
        format.setCurrency(cur)
        val digits = cur.getDefaultFractionDigits()
        format.setMinimumFractionDigits(digits)
        format.setMaximumFractionDigits(digits)

        val ret = format.format(number)

        return ret
    } catch (e: Throwable) {
        return number.toString() + currency
    }

}

/**
 * format number by currency
 *
 * @param number
 * @param currency
 * @return
 */
fun formatNumberByCurrency(number: Any, currency: String): String {
    try {
        return formatNumberByCurrency(number, Currency.getInstance(currency))
    } catch (e: Exception) {
        return number.toString()
    }

}

/**
 * @param number
 * @param currency
 * @param fractionDigits
 * @return
 */
fun formatNumberByCurrency(number: Any, currency: String, fractionDigits: Int): String {
    try {
        return formatNumberByCurrency(number, Currency.getInstance(currency), fractionDigits)
    } catch (e: Exception) {
        return number.toString()
    }

}

/**
 * format number by currency
 *
 * @param number
 * @param currency
 * @return
 */
fun formatNumberByCurrency(number: Any, currency: Currency): String {
    return formatNumberByCurrency(number, currency, currency.getDefaultFractionDigits())
}

/**
 * @param number
 * @param currency
 * @param fractionDigits
 * @return
 */
fun formatNumberByCurrency(number: Any, currency: Currency, fractionDigits: Int): String {
    val nf: NumberFormat
    nf = NumberFormat.getInstance()
    try {
        nf.currency = currency
        nf.minimumFractionDigits = fractionDigits
        nf.maximumFractionDigits = fractionDigits
        return nf.format(number)
    } catch (e: Exception) {
        return number.toString()
    }

}

/**
 * @param number
 * @param fractionDigits
 * @return
 */
fun formatNumber(number: Any, fractionDigits: Int): String {
    val nf: NumberFormat
    nf = NumberFormat.getNumberInstance()
    nf.minimumFractionDigits = fractionDigits
    nf.maximumFractionDigits = fractionDigits
    return nf.format(number)
}

/**
 * @param left
 * @param right
 * @param locale
 * @param caseSensitive
 * @return
 */
fun compareLettersAccentSame(left: String, right: String, locale: Locale, caseSensitive: Boolean): Int {
    var left = left
    var right = right
    val collator = Collator.getInstance(locale)
    if (!caseSensitive) {
        left = left.toUpperCase(locale)
        right = right.toUpperCase(locale)
    }
    collator.strength = Collator.PRIMARY
    return collator.compare(left, right)
}

/**
 * @param context
 * @param msg
 */
fun toastMessage(context: Context, msg: String) {
    try {
        val t = Toast.makeText(context, msg, Toast.LENGTH_LONG)
        ((t.getView() as ViewGroup).getChildAt(0) as TextView).gravity = Gravity.CENTER
        t.show()
    } catch (e: Exception) {
        Debug.logException(e)    // closed app just before this
    }

}

/**
 * @param context
 * @param msgResId
 */
fun toastMessage(context: Context, msgResId: Int) {
    try {
        val t = Toast.makeText(context, msgResId, Toast.LENGTH_LONG)
        ((t.getView() as ViewGroup).getChildAt(0) as TextView).gravity = Gravity.CENTER
        t.show()
    } catch (e: Exception) {
        Debug.logException(e)    // closed app just before this
    }

}

/**
 * @param root
 * @param font
 */
fun setTypefaceRec(root: View, font: Typeface) {
    if (root is ViewGroup) {
        for (i in 0..root.childCount - 1) {
            setTypefaceRec(root.getChildAt(i), font)
        }
    } else if (root is TextView) {
        root.typeface = font
    }
}

fun hasExternalStorage(): Boolean {
    val state = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
}

/**
 * read a file
 *
 * @param file
 * @return
 * @throws IOException
 */
@Throws(IOException::class)
fun readFile(file: String): ByteArray {
    // Open file
    var f: RandomAccessFile? = null
    var data: ByteArray? = null
    try {
        f = RandomAccessFile(File(file), "r")

        // Get and check length
        val longlength = f!!.length()
        val length = longlength.toInt()
        if (length.toLong() != longlength) throw IOException("File too big")

        // Read file and return data
        data = ByteArray(length)
        f!!.readFully(data)
        return data
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (f != null) {
        f!!.close()
    }

    return data!!
}

/**
 * for locations
 *
 * @return
 */
val calendarTimeInMillisUTC: Long
    get() {
        val calendar = Calendar.getInstance()
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"))
        return calendar.getTimeInMillis()
    }

/**
 * calendar base uri - different from among phones
 *
 * @return
 */
val calendarUriBase: String
    get() {
        val activity = Global.currentActivity

        var calendarUriBase: String? = null
        var calendars = Uri.parse("content://calendar/calendars")
        var managedCursor: Cursor? = null
        try {
            managedCursor = activity!!.managedQuery(calendars, null, null, null, null)
        } catch (e: Exception) {
            try {
                calendars = Uri.parse("content://calendarEx/calendars")
                managedCursor = activity!!.managedQuery(calendars, null, null, null, null)
            } catch (ex: Exception) {
            }

        }

        if (managedCursor != null) {
            calendarUriBase = calendars.toString().replace("/calendars", "")
        } else {
            calendars = Uri.parse("content://com.android.calendar/calendars")
            try {
                managedCursor = activity!!.managedQuery(calendars, null, null, null, null)
            } catch (e: Exception) {
            }

            if (managedCursor != null) {
                calendarUriBase = "content://com.android.calendar/"
            }
        }

        return calendarUriBase!!
    }

/**
 * put event to default calendar
 *
 * @param context
 * @param title
 * @param description
 * @param startInMillis
 * @param endInMillis
 * @return
 */
fun putIntoDefaultCalendar(context: Context, title: String, description: String,
                           startInMillis: Long, endInMillis: Long): Long? {
    val event = ContentValues()

    try {
        val cBase = calendarUriBase

        val cr = context.contentResolver
        val cursor = cr.query(Uri.parse(cBase + "calendars"),
                arrayOf("_id"), null, null, null)
        cursor.moveToLast()
        val cid = Integer.parseInt(cursor.getString(0))

        event.put("calendar_id", cid)
        event.put("title", title)
        event.put("description", description)
        event.put("dtstart", startInMillis.toString())
        event.put("dtend", endInMillis.toString())
        event.put("eventTimezone", Global.timezone.id)

        val eventUri = cr.insert(Uri.parse(cBase + "events"), event)
        return java.lang.Long.parseLong(eventUri.lastPathSegment)
    } catch (e: Throwable) {
        return null
    }

}

/**
 * remove from calendar by id
 *
 * @param context
 * @param calId
 * @return
 */
fun removeFromDefaultCalendar(context: Context, calId: Long): Int? {
    try {
        val cBase = calendarUriBase
        val cr = context.getContentResolver()

        var iNumRowsDeleted = 0

        try {
            val eventUri = ContentUris.withAppendedId(Uri.parse(cBase + "events"), calId)
            iNumRowsDeleted = cr.delete(eventUri, null, null)
        } catch (e: Throwable) {
        }

        if (iNumRowsDeleted == 0) {

            // maybe another method?
            val cursor = cr.query(Uri.parse(cBase + "events"), arrayOf("_id"), "calendar_id=" + calId, null, null)
            do {
                val eventId = cursor.getLong(cursor.getColumnIndex("_id"))
                cr.delete(ContentUris.withAppendedId(Uri.parse(cBase + "events"), eventId), null, null)
                ++iNumRowsDeleted
            } while (cursor.moveToNext())
            cursor.close()
        }

        return iNumRowsDeleted
    } catch (e: Throwable) {
        return null
    }

}

/**
 *
 * @param v
 * @return
 */
fun enableListViewInScrollView(v: View) {
    val parent = findScrollViewParent(v) ?: return
    v.setOnTouchListener { v, event ->
        parent!!.requestDisallowInterceptTouchEvent(true)
        false
    }
}

/**
 *
 * @param v
 * @return
 */
fun findScrollViewParent(v: View): ScrollView? {
    val parent = v.getParent()
    if (parent == null || parent !is View) {
        return null
    }
    if (parent is ScrollView) {
        return parent
    }

    return findScrollViewParent(parent)
}

/**
 *
 * @return
 */
fun getActionBarHeight(context: Context): Int {
    var actionBarHeight = 0
    val tv = TypedValue()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv,
                true))
            actionBarHeight = TypedValue.complexToDimensionPixelSize(
                    tv.data, context.getResources().getDisplayMetrics())
    } else {
        actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,
                context.getResources().getDisplayMetrics())
    }
    return actionBarHeight
}

/** ------------------------------------------------------------------------------------------*/

/**
 * serialize object
 *
 * @param o
 * @return
 */
fun serializeObject(o: Any): ByteArray? {
    val bos = ByteArrayOutputStream();

    try {
        val out = ObjectOutputStream(bos);
        out.writeObject(o);
        out.close();

        // Get the bytes of the serialized object
        val buf = bos.toByteArray();

        return buf;
    } catch(error: IOException) {
        error.printStackTrace();
        return null;
    }
}

/**
 * deserialize object
 *
 * @param b
 * @return
 */
fun deserializeObject(b: ByteArray): Serializable? {
    try {
        val input = ObjectInputStream(ByteArrayInputStream(b))
        val result = input.readObject()
        input.close();

        return result as Serializable;
    } catch(error: ClassNotFoundException) {
        error.printStackTrace();
        return null;
    } catch(error: IOException) {
        error.printStackTrace();
        return null;
    }
}