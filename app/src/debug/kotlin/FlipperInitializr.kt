import android.content.Context
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.android.utils.FlipperUtils
import com.facebook.flipper.core.FlipperClient
import com.facebook.flipper.plugins.crashreporter.CrashReporterPlugin
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.leakcanary2.LeakCanary2FlipperPlugin
import com.facebook.flipper.plugins.network.FlipperOkhttpInterceptor
import com.facebook.flipper.plugins.network.NetworkFlipperPlugin
import com.facebook.soloader.SoLoader
import com.sandeep.readnfc.BuildConfig

class FlipperInitializr private constructor(context: Any) {

    private lateinit var networkPlugin: NetworkFlipperPlugin

    init {
        if (context is Context) {
            SoLoader.init(context, false)
            // Configure LeakCanary to send leak events to Flipper
            leakcanary.LeakCanary.config = leakcanary.LeakCanary.config.run {
                copy(eventListeners = eventListeners + com.facebook.flipper.plugins.leakcanary2.FlipperLeakEventListener())
            }
            if (BuildConfig.DEBUG && FlipperUtils.shouldEnableFlipper(context)) {
                val client: FlipperClient = AndroidFlipperClient.getInstance(context)
                networkPlugin = NetworkFlipperPlugin()
                client.addPlugin(networkPlugin)
                client.addPlugin(DatabasesFlipperPlugin(context))
                client.addPlugin(InspectorFlipperPlugin(context, DescriptorMapping.withDefaults()))
                client.addPlugin(CrashReporterPlugin.getInstance())
                client.addPlugin(LeakCanary2FlipperPlugin())
                // client.addPlugin(SharedPreferencesFlipperPlugin(context, "ReadNfc"))
                client.start()
            }
        }
    }

    fun getNetworkInspector(): FlipperOkhttpInterceptor? {
        return if (::networkPlugin.isInitialized) FlipperOkhttpInterceptor(networkPlugin) else null
    }

    companion object : SingletonHolder<FlipperInitializr, Any>(::FlipperInitializr)
}
