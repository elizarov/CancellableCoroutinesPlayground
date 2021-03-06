package coroutines.javafx

import coroutines.current.setThreadDefaultCoroutineContext
import coroutines.run.asyncRun
import coroutines.ui.JavaFx
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage

fun main(args: Array<String>) {
    Application.launch(FxTestApp::class.java, *args)
}

class FxTestApp : Application() {
    init {
        // this has to be first init section
        setThreadDefaultCoroutineContext(JavaFx)
    }

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

    fun startRect() {
        val rect = Rectangle(20.0, 20.0).apply {
            fill = Color.RED
        }
        root.children += rect
        asyncRun {
            var vx = 5
            var vy = 5
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
            }
        }
    }
}