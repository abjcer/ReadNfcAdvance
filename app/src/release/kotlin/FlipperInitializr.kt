class FlipperInitializr private constructor(context: Any) {

    companion object : SingletonHolder<FlipperInitializr, Any>(::FlipperInitializr)

    fun getNetworkInspector(): DummyInterceptor? {
        return DummyInterceptor()
    }
}
