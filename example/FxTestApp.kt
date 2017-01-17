import kotlinx.coroutines.experimental.runSuspending
import kotlinx.coroutines.experimental.javafx.JavaFx
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import kotlinx.coroutines.experimental.delay

fun main(args: Array<String>) {
    Application.launch(FxTestApp::class.java, *args)
}

class FxTestApp : Application() {
    val startButton = Button("Start").apply {
        setOnAction { startRect() }
    }

    val root = Pane().apply {
        children += startButton
    }

    val scene = Scene(root, 600.0, 400.0)

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Hello world!"
        primaryStage.scene = scene
        primaryStage.show()
    }

    var rectIndex = 0

    fun startRect() {
        val rect = Rectangle(20.0, 20.0).apply {
            fill = Color.RED
        }
        root.children += rect
        val index = ++rectIndex
        runSuspending {
            log("Started new coroutine #$index")
            var vx = 5
            var vy = 5
            var counter = 0
            while (true) {
                JavaFx.awaitPulse()
                rect.x += vx
                rect.y += vy
                val xRange = 0.0 .. scene.width - rect.width
                val yRange = 0.0 .. scene.height - rect.height
                if (rect.x !in xRange ) {
                    rect.x = rect.x.coerceIn(xRange)
                    vx = -vx
                }
                if (rect.y !in yRange) {
                    rect.y = rect.y.coerceIn(yRange)
                    vy = -vy
                }
                if (counter++ > 100) {
                    counter = 0
                    delay(1000) // pause a bit
                    log("Delayed #$index for a while, resume and turn")
                    val t = vx
                    vx = vy
                    vy = -t
                }
            }
        }
    }
}