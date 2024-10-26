package tests

import box2dLight.PointLight
import box2dLight.RayHandler
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.World

class SimpleTest : ApplicationAdapter() {

    private var camera: OrthographicCamera? = null
    private var rayHandler: RayHandler? = null
    private var world: World? = null

    override fun create() {
        camera = OrthographicCamera(48F, 32F)
        camera?.update()
        world = World(Vector2(0F, -10F), true)
        rayHandler = RayHandler(world)
        PointLight(rayHandler, 32)
    }

    override fun render() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        world?.step(Gdx.graphics.deltaTime, 8, 3)
        rayHandler?.setCombinedMatrix(camera)
        rayHandler?.updateAndRender()
    }
}

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration()

    config.title = "Simple Test"
    config.width = 800
    config.height = 480
    config.samples = 4
    config.depth = 0
    config.vSyncEnabled = true
    config.fullscreen = false

    LwjglApplication(SimpleTest(), config)
}
