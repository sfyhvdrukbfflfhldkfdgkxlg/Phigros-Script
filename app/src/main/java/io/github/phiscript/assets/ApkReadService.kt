package io.github.phiscript.assets

import android.content.Context
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.ServiceSpecificException
import org.json.JSONArray
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Shizuku 13+ UserService (not Android Service); no arbitrary package/path/command parameters. */
@Suppress("unused")
class ApkReadService(private val context: Context) : IPhigrosApkReader.Stub() {
    private val clientUid = context.applicationInfo.uid
    private val writers = ThreadPoolExecutor(2, 2, 15, TimeUnit.SECONDS, ArrayBlockingQueue(4),
        { task -> Thread(task, "PhigrosApkPipe").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    override fun listEntries(versionCode: Long, lastUpdateTime: Long): ParcelFileDescriptor {
        checkCaller()
        val snapshot = checkedSnapshot(versionCode, lastUpdateTime)
        return pipe { output ->
            checkUnchanged(snapshot)
            val bytes = JSONArray(ApkFiles.entries(snapshot)).toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > ApkFiles.MAX_LIST_BYTES) throw ApkAccessException("安装包目录超过读取上限。")
            output.write(bytes)
        }
    }
    override fun readAsset(entry: String?, maxBytes: Int, versionCode: Long, lastUpdateTime: Long): ParcelFileDescriptor {
        checkCaller()
        val name = entry ?: throw ServiceSpecificException(1, "缺少资源名称。")
        try { ApkFiles.validateRead(name, maxBytes) }
        catch (e: Exception) { throw ServiceSpecificException(1, e.message ?: "资源参数无效。") }
        val snapshot = checkedSnapshot(versionCode, lastUpdateTime)
        return pipe { output ->
            checkUnchanged(snapshot); ApkFiles.copyAsset(snapshot, name, maxBytes, output)
        }
    }
    override fun destroy() {
        val uid = Binder.getCallingUid()
        if (uid != clientUid && uid != 0 && uid != 2000) throw SecurityException("Caller does not own this service.")
        writers.shutdownNow(); exitProcess(0)
    }
    private fun checkCaller() {
        if (Binder.getCallingUid() != clientUid) throw SecurityException("Caller does not own this service.")
    }
    private fun checkedSnapshot(versionCode: Long, lastUpdateTime: Long): PackageSnapshot {
        try {
            return packageSnapshot(context).also {
                if (it.versionCode != versionCode || it.lastUpdateTime != lastUpdateTime)
                    throw ApkAccessException("Phigros 已更新，请重新扫描。")
            }
        } catch (e: Exception) { throw ServiceSpecificException(2, e.message ?: "无法查询 Phigros 安装信息。") }
    }
    private fun checkUnchanged(snapshot: PackageSnapshot) {
        if (packageSnapshot(context) != snapshot) throw ApkAccessException("Phigros 已更新，请重新扫描。")
    }
    private fun pipe(writer: (OutputStream) -> Unit): ParcelFileDescriptor {
        val ends = ParcelFileDescriptor.createReliablePipe()
        try {
            writers.execute {
                val output = ParcelFileDescriptor.AutoCloseOutputStream(ends[1])
                try { writer(output); output.flush() }
                catch (e: Exception) {
                    runCatching { ends[1].closeWithError((e.message ?: "APK 读取失败。").take(1000)) }
                } finally { runCatching { output.close() } }
            }
        } catch (e: Exception) {
            runCatching { ends[0].close() }; runCatching { ends[1].close() }
            throw ServiceSpecificException(3, "资源读取繁忙，请稍后重试。")
        }
        return ends[0]
    }
}
