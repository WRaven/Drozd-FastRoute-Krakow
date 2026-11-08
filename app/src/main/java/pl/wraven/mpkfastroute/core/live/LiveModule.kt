package pl.wraven.mpkfastroute.core.live

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object LiveModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createHttpClient(REQUEST_TIMEOUT_MILLIS)

    @Provides
    @Singleton
    @Named(UPDATE_HTTP_CLIENT)
    fun provideUpdateHttpClient(): HttpClient = createHttpClient(UPDATE_REQUEST_TIMEOUT_MILLIS)

    private fun createHttpClient(timeoutMillis: Long): HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = timeoutMillis
            requestTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis
        }
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideTtssLiveMapper(): TtssLiveMapper = TtssLiveMapper()

    private const val REQUEST_TIMEOUT_MILLIS = 10_000L
    private const val UPDATE_REQUEST_TIMEOUT_MILLIS = 30_000L
    private const val UPDATE_HTTP_CLIENT = "githubUpdate"
}
