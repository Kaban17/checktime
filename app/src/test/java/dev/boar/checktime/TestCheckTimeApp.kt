package dev.boar.checktime

/**
 * Robolectric подставляет `Test<ApplicationClassName>` из того же пакета вместо реального
 * Application, если такой класс есть — без изменений в @Config. Не даёт каждому тесту в
 * сьюте создавать файловый Room + неуправляемую IO-корутину (см. CheckTimeApp.onCreate).
 */
class TestCheckTimeApp : CheckTimeApp() {
    override fun createContainer(): AppContainer =
        AppContainer(this, db = inMemoryDb(), settings = tempSettingsRepository())
}
