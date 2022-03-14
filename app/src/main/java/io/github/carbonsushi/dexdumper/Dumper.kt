package io.github.carbonsushi.dexdumper

import android.app.AndroidAppHelper
import android.content.Context
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import dalvik.system.DexFile
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.SELinuxHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.nio.ByteBuffer

class Dumper : IXposedHookLoadPackage {
    private val contentResolver by lazy {
        var context: Context? = null
        while (context == null) {
            context = AndroidAppHelper.currentApplication()
        }
        context!!.contentResolver
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        EzXHelperInit.initHandleLoadPackage(lpparam!!)

        getDexFileMethod("openDexFile").hookBefore { param ->
            val filePath = param.args[0] as String
            saveDexAsync(
                SELinuxHelper.getAppDataFileService().readFile(filePath),
                "${lpparam.packageName}_$filePath.dex"
            )
        }

        getDexFileMethod("openInMemoryDexFiles").hookBefore { param ->
            for (dex in param.args[0] as Array<*>) {
                val byteBuffer = (dex as ByteBuffer).duplicate()
                val byteArray = ByteArray(byteBuffer.capacity())
                byteBuffer.rewind()
                byteBuffer.get(byteArray)
                saveDexAsync(byteArray, "${lpparam.packageName}_openInMemoryDexFiles.dex")
            }
        }
    }

    private fun getDexFileMethod(methodName: String): Method = findMethod(DexFile::class.java) {
        name == methodName
    }

    private fun saveDexAsync(dex: ByteArray, saveName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            contentResolver.openOutputStream(
                contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValuesOf(Pair(MediaStore.MediaColumns.DISPLAY_NAME, saveName))
                )!!
            ).use { outputStream ->
                outputStream!!.write(dex)
            }
        }
    }
}