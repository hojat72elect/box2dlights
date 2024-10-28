package tests

import com.badlogic.gdx.utils.Array as GdxArray
import box2dLight.ChainLight
import box2dLight.ConeLight
import box2dLight.DirectionalLight
import box2dLight.Light
import box2dLight.PointLight
import box2dLight.RayHandler
import box2dLight.RayHandlerOptions
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType
import com.badlogic.gdx.physics.box2d.ChainShape
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.QueryCallback
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.physics.box2d.joints.MouseJoint
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef
import com.badlogic.gdx.utils.viewport.FitViewport

class Box2dLightCustomShaderTest : InputAdapter(), ApplicationListener {

    private var camera: OrthographicCamera? = null
    private var viewport: FitViewport? = null
    private var batch: SpriteBatch? = null
    private var font: BitmapFont? = null

    private var world: World? = null
    private val balls = ArrayList<Body>(NUM_BALLS)
    private var groundBody: Body? = null
    private var mouseJoint: MouseJoint? = null
    private var hitBody: Body? = null

    //    pixel perfect projection for font rendering
    private val normalProjection = Matrix4()
    private var showText = true


    private var rayHandler: RayHandler? = null
    private val lights = ArrayList<Light>(NUM_BALLS)
    private var sunDirection = -90f
    private var bg: Texture? = null
    private var bgN: Texture? = null
    private var objectReg: TextureRegion? = null
    private var objectRegN: TextureRegion? = null
    private var normalFbo: FrameBuffer? = null
    private var assetArray = GdxArray<DeferredObject>()
    private var marble: DeferredObject? = null
    private var lightShader: ShaderProgram? = null
    private var normalShader: ShaderProgram? = null
    private var drawNormals = false
    private val bgColor = Color()
    private var physicsTimeLeft = 0f
    private var aika = 0L
    private var times = 0


    // we instantiate this vector and the callback here so we don't irritate the C.
    private val testPoint = Vector3()
    private val callback = QueryCallback { fixture ->
        if (fixture.body === groundBody) return@QueryCallback true
        if (fixture.testPoint(testPoint.x, testPoint.y)) {
            hitBody = fixture.body
            false
        } else true
    }
    private val target = Vector2()

    /*
     * Here we have 4 types of lights to use:
     * 0 - PointLight
     * 1 - ConeLight
     * 2 - ChainLight
     * 3 - DirectionalLight
     */
    private var lightsType = 0
    private var once = true


    override fun create() {


        bg = Texture(Gdx.files.internal("test/data/bg-deferred.png"))
        bgN = Texture(Gdx.files.internal("test/data/bg-deferred-n.png"))

        MathUtils.random.setSeed(Long.MIN_VALUE)

        camera = OrthographicCamera(viewportWidth, viewportHeight)
        camera?.update()

        viewport = FitViewport(viewportWidth, viewportHeight, camera)

        batch = SpriteBatch()
        font = BitmapFont()
        font?.color = Color.RED

        val marbleD = TextureRegion(Texture(Gdx.files.internal("test/data/marble.png")))

        val marbleN = TextureRegion(Texture(Gdx.files.internal("test/data/marble-n.png")))

        marble = DeferredObject(marbleD, marbleN)
        marble?.width = RADIUS * 2
        marble!!.height = RADIUS * 2

        createPhysicsWorld()
        Gdx.input.inputProcessor = this

        normalProjection.setToOrtho2D(0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())


        normalShader = createNormalShader()

        lightShader = createLightShader()
        val options = RayHandlerOptions()
        options.setDiffuse(true)
        options.setGammaCorrection(true)
        rayHandler = object : RayHandler(world, Gdx.graphics.width, Gdx.graphics.height, options) {
            override fun updateLightShaderPerLight(light: Light) {
                // light position must be normalized
                val x = (light.x) / viewportWidth
                val y = (light.y) / viewportHeight
                lightShader?.setUniformf("u_lightpos", x, y, 0.05f)
                lightShader?.setUniformf("u_intensity", 5f)
            }
        }
        rayHandler?.setLightShader(lightShader)
        rayHandler?.setAmbientLight(0.1f, 0.1f, 0.1f, 0.5f)
        rayHandler?.setBlurNum(0)

        initPointLights()


        objectReg = TextureRegion(Texture(Gdx.files.internal("test/data/object-deferred.png")))
        objectRegN = TextureRegion(Texture(Gdx.files.internal("test/data/object-deferred-n.png")))

        for (x in 0..3) {
            for (y in 0..2) {
                val deferredObject = DeferredObject(objectReg!!, objectRegN!!)
                deferredObject.x = 4 + x * (deferredObject.diffuse.regionWidth * SCALE + 8)
                deferredObject.y = 4 + y * (deferredObject.diffuse.regionHeight * SCALE + 7)
                deferredObject.color[MathUtils.random(0.5f, 1f), MathUtils.random(0.5f, 1f), MathUtils.random(0.5f, 1f)] = 1f
                if (x > 0) deferredObject.rot = true
                deferredObject.rotation = MathUtils.random(90).toFloat()
                assetArray.add(deferredObject)
            }
        }
        once = false
        normalFbo = FrameBuffer(Pixmap.Format.RGB565, Gdx.graphics.width, Gdx.graphics.height, false)

    }

    private fun createLightShader(): ShaderProgram {
        // Shader adopted from https://github.com/mattdesl/lwjgl-basics/wiki/ShaderLesson6
        val vertexShader =
            ("""attribute vec4 vertex_positions;
attribute vec4 quad_colors;
attribute float s;
uniform mat4 u_projTrans;
varying vec4 v_color;
void main()
{
   v_color = s * quad_colors;
   gl_Position =  u_projTrans * vertex_positions;
}
""")

        val fragmentShader = ("""#ifdef GL_ES
precision lowp float;
#define MED mediump
#else
#define MED 
#endif
varying vec4 v_color;
uniform sampler2D u_normals;
uniform vec3 u_lightpos;
uniform vec2 u_resolution;
uniform float u_intensity = 1.0;
void main()
{
  vec2 screenPos = gl_FragCoord.xy / u_resolution.xy;
  vec3 NormalMap = texture2D(u_normals, screenPos).rgb;   vec3 LightDir = vec3(u_lightpos.xy - screenPos, u_lightpos.z);
  vec3 N = normalize(NormalMap * 2.0 - 1.0);
  vec3 L = normalize(LightDir);
  float maxProd = max(dot(N, L), 0.0);
  gl_FragColor = v_color * maxProd * u_intensity;
}""")

        ShaderProgram.pedantic = false
        val lightShader = ShaderProgram(
            vertexShader,
            fragmentShader
        )
        if (!lightShader.isCompiled) {
            Gdx.app.log("ERROR", lightShader.log)
        }

        lightShader.begin()
        lightShader.setUniformi("u_normals", 1)
        lightShader.setUniformf("u_resolution", Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        lightShader.end()

        return lightShader
    }

    private fun createNormalShader(): ShaderProgram {
        val vertexShader = ("""attribute vec4 ${ShaderProgram.POSITION_ATTRIBUTE};
attribute vec4 ${ShaderProgram.COLOR_ATTRIBUTE};
attribute vec2 ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
uniform mat4 u_projTrans;
uniform float u_rot;
varying vec4 v_color;
varying vec2 v_texCoords;
varying mat2 v_rot;

void main()
{
   vec2 rad = vec2(-sin(u_rot), cos(u_rot));
   v_rot = mat2(rad.y, -rad.x, rad.x, rad.y);
   v_color = ${ShaderProgram.COLOR_ATTRIBUTE};
   v_color.a = v_color.a * (255.0/254.0);
   v_texCoords = ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
   gl_Position =  u_projTrans * ${ShaderProgram.POSITION_ATTRIBUTE};
}
""")
        val fragmentShader = ("""#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP 
#endif
varying LOWP vec4 v_color;
varying vec2 v_texCoords;
varying mat2 v_rot;
uniform sampler2D u_texture;
void main()
{
  vec4 normal = texture2D(u_texture, v_texCoords).rgba;
  vec2 rotated = v_rot * (normal.xy * 2.0 - 1.0);
  rotated = (rotated.xy / 2.0 + 0.5 );
  gl_FragColor = vec4(rotated.xy, normal.z, normal.a);
}""")

        val shader = ShaderProgram(vertexShader, fragmentShader)
        require(shader.isCompiled) { "Error compiling shader: " + shader.log }
        return shader
    }


    override fun resize(width: Int, height: Int) {
        viewport?.update(width, height, true)
    }

    override fun render() {


        // Rotate directional light like sun :)
        if (lightsType == 3) {
            sunDirection += Gdx.graphics.deltaTime * 4f
            lights[0].direction = sunDirection
        }

        camera?.update()

        val stepped: Boolean = fixedStep(Gdx.graphics.deltaTime)
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        batch?.projectionMatrix = camera?.combined
        for (deferredObject in assetArray) {
            deferredObject.update()
        }
        normalFbo?.begin()
        batch?.disableBlending()
        batch?.begin()
        batch?.shader = normalShader
        normalShader?.setUniformf("u_rot", 0f)
        val bgWidth = bgN!!.width * SCALE
        val bgHeight = bgN!!.height * SCALE
        for (x in 0..5) {
            for (y in 0..5) {
                batch?.draw(bgN, x * bgWidth, y * bgHeight, bgWidth, bgHeight)
            }
        }
        batch?.enableBlending()
        for (deferredObject in assetArray) {
            normalShader?.setUniformf("u_rot", MathUtils.degreesToRadians * deferredObject.rotation)
            deferredObject.drawNormal(batch)
            // flush the batch or otherwise uniform wont change.
            // TODO this is baaaad, maybe modify SpriteBatch to add rotation in the attributes? Flushing after each defeats the point of batch
            batch?.flush()
        }
        for (i in 0 until NUM_BALLS) {
            val ball = balls[i]
            val position = ball.position
            val angle = MathUtils.radiansToDegrees * ball.angle
            marble?.x = position.x - RADIUS
            marble?.y = position.y - RADIUS
            marble?.rotation = angle
            normalShader?.setUniformf("u_rot", MathUtils.degreesToRadians * marble!!.rotation)
            marble?.drawNormal(batch)
            // TODO same as above
            batch?.flush()
        }
        batch?.end()
        normalFbo?.end()

        val normals = normalFbo?.colorBufferTexture

        batch?.disableBlending()
        batch?.begin()
        batch?.shader = null
        if (drawNormals) {
            // draw flipped so it looks ok
            batch!!.draw(
                normals, 0f, 0f,  // x, y
                viewportWidth / 2, viewportHeight / 2,  // origx, origy
                viewportWidth, viewportHeight,  // width, height
                1f, 1f,  // scale x, y
                0f,  // rotation
                0, 0, normals!!.width, normals.height,  // tex dimensions
                false, true
            ) // flip x, y
        } else {
            for (x in 0..5) {
                for (y in 0..5) {
                    batch?.color = bgColor.set(x / 5.0f, y / 6.0f, 0.5f, 1f)
                    batch?.draw(bg, x * bgWidth, y * bgHeight, bgWidth, bgHeight)
                }
            }
            batch?.color = Color.WHITE
            batch?.enableBlending()
            for (deferredObject in assetArray) {
                deferredObject.draw(batch)
            }
            for (i in 0 until NUM_BALLS) {
                val ball = balls[i]
                val position = ball.position
                val angle = MathUtils.radiansToDegrees * ball.angle
                marble?.x = position.x - RADIUS
                marble?.y = position.y - RADIUS
                marble?.rotation = angle
                marble?.draw(batch)
            }
        }
        batch?.end()

        if (!drawNormals) {
            rayHandler?.setCombinedMatrix(camera)
            if (stepped) rayHandler?.update()
            normals?.bind(1)
            rayHandler?.render()
        }

        val time = System.nanoTime()

        val atShadow = rayHandler?.pointAtShadow(
            testPoint.x,
            testPoint.y
        )
        aika += System.nanoTime() - time


        if (showText) {
            batch?.projectionMatrix = normalProjection
            batch?.begin()

            font?.draw(
                batch,
                "F1 - PointLight",
                0f, Gdx.graphics.height.toFloat()
            )
            font?.draw(
                batch,
                "F2 - ConeLight",
                0f, (Gdx.graphics.height - 15).toFloat()
            )
            font?.draw(
                batch,
                "F3 - ChainLight",
                0f, (Gdx.graphics.height - 30).toFloat()
            )
            font?.draw(
                batch,
                "F4 - DirectionalLight",
                0f, (Gdx.graphics.height - 45).toFloat()
            )
            font?.draw(
                batch,
                "F5 - random lights colors",
                0f, (Gdx.graphics.height - 75).toFloat()
            )
            font?.draw(
                batch,
                "F6 - random lights distance",
                0f, (Gdx.graphics.height - 90).toFloat()
            )
            font?.draw(
                batch,
                "F7 - toggle drawing of normals",
                0f, (Gdx.graphics.height - 105).toFloat()
            )
            font?.draw(
                batch,
                "F9 - default blending (1.3)",
                0f, (Gdx.graphics.height - 120).toFloat()
            )
            font?.draw(
                batch,
                "F10 - over-burn blending (default in 1.2)",
                0f, (Gdx.graphics.height - 135).toFloat()
            )
            font?.draw(
                batch,
                "F11 - some other blending",
                0f, (Gdx.graphics.height - 150).toFloat()
            )

            font?.draw(
                batch,
                "F12 - toggle help text",
                0f, (Gdx.graphics.height - 180).toFloat()
            )

            font?.draw(
                batch,
                (Gdx.graphics.framesPerSecond
                    .toString() + "mouse at shadows: " + atShadow
                        + " time used for shadow calculation:"
                        + aika / ++times + "ns"), 0f, 20f
            )

            batch?.end()
        }
    }

    fun clearLights() {
        if (!lights.isEmpty()) {
            for (light in lights) {
                light.remove()
            }
            lights.clear()
        }
        groundBody?.isActive = true
    }

    fun initPointLights() {
        clearLights()
        for (i in 0 until NUM_BALLS) {
            val light = PointLight(rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 0f, 0f)
            light.attachToBody(balls[i], RADIUS / 2f, RADIUS / 2f)
            light.setColor(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1f)
            lights.add(light)
        }
    }

    fun initConeLights() {
        clearLights()
        for (i in 0 until NUM_BALLS) {
            val light = ConeLight(rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 0f, 0f, 0f, MathUtils.random(15f, 40f))
            light.attachToBody(balls[i], RADIUS / 2f, RADIUS / 2f, MathUtils.random(0f, 360f))
            light.setColor(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1f)
            lights.add(light)
        }
    }


    fun initChainLights() {
        clearLights()
        for (i in 0 until NUM_BALLS) {
            val light = ChainLight(rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 1, floatArrayOf(-5f, 0f, 0f, 3f, 5f, 0f))
            light.attachToBody(balls[i], MathUtils.random(0f, 360f))
            light.setColor(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1f)
            lights.add(light)
        }
    }

    fun initDirectionalLight() {
        clearLights()

        groundBody?.isActive = false
        sunDirection = MathUtils.random(0f, 360f)

        val light = DirectionalLight(rayHandler, 4 * RAYS_PER_BALL, null, sunDirection)
        lights.add(light)
    }

    private fun fixedStep(delta: Float): Boolean {
        physicsTimeLeft += delta
        if (physicsTimeLeft > MAX_TIME_PER_FRAME) physicsTimeLeft = MAX_TIME_PER_FRAME

        var stepped = false
        while (physicsTimeLeft >= TIME_STEP) {
            world?.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS)
            physicsTimeLeft -= TIME_STEP
            stepped = true
        }
        return stepped
    }

    private fun createPhysicsWorld() {
        world = World(Vector2(0f, 0f), true)

        val chainShape = ChainShape()
        chainShape.createLoop(arrayOf(Vector2(0f, 0f), Vector2(viewportWidth, 0f), Vector2(viewportWidth, viewportHeight), Vector2(0f, viewportHeight)))
        val chainBodyDef = BodyDef()
        chainBodyDef.type = BodyType.StaticBody
        groundBody = world?.createBody(chainBodyDef)
        groundBody?.createFixture(chainShape, 0f)
        chainShape.dispose()
        createBoxes()
    }

    private fun createBoxes() {
        val ballShape = CircleShape()
        ballShape.radius = RADIUS

        val def = FixtureDef()
        def.restitution = 0.9f
        def.friction = 0.01f
        def.shape = ballShape
        def.density = 1f
        val boxBodyDef = BodyDef()
        boxBodyDef.type = BodyType.DynamicBody

        for (i in 0 until NUM_BALLS) {
            // Create the BodyDef, set a random position above the
            // ground and create a new body
            boxBodyDef.position.x = 1 + (Math.random() * (viewportWidth - 2)).toFloat()
            boxBodyDef.position.y = 1 + (Math.random() * (viewportHeight - 2)).toFloat()
            val boxBody = world!!.createBody(boxBodyDef)
            boxBody.createFixture(def)
            boxBody.isFixedRotation = true
            balls.add(boxBody)
        }
        ballShape.dispose()
    }

    override fun touchDown(x: Int, y: Int, pointer: Int, newParam: Int): Boolean {


        // translate the mouse coordinates to world coordinates
        testPoint[x.toFloat(), y.toFloat()] = 0f
        camera?.unproject(testPoint)


        // ask the world which bodies are within the given
        // bounding box around the mouse pointer
        hitBody = null
        world?.QueryAABB(
            callback, testPoint.x - 0.1f, testPoint.y - 0.1f,
            testPoint.x + 0.1f, testPoint.y + 0.1f
        )


        // if we hit something we create a new mouse joint
        // and attach it to the hit body.
        if (hitBody != null) {
            val def = MouseJointDef()
            def.bodyA = groundBody
            def.bodyB = hitBody
            def.collideConnected = true
            def.target[testPoint.x] = testPoint.y
            def.maxForce = 1000.0f * hitBody!!.mass

            mouseJoint = world?.createJoint(def) as MouseJoint?
            hitBody?.isAwake = true
        }

        return false
    }

    override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {

        camera?.unproject(testPoint.set(x.toFloat(), y.toFloat(), 0f))
        target[testPoint.x] = testPoint.y


        // if a mouse joint exists we simply update
        // the target of the joint based on the new
        // mouse coordinates
        if (mouseJoint != null) {
            mouseJoint?.target = target
        }
        return false
    }

    override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        // if a mouse joint exists we simply destroy it
        if (mouseJoint != null) {
            world?.destroyJoint(mouseJoint)
            mouseJoint = null
        }
        return false
    }

    override fun pause() {}

    override fun resume() {}

    override fun dispose() {
        rayHandler?.dispose()
        world?.dispose()
        objectReg?.texture?.dispose()
        objectRegN?.texture?.dispose()
        normalFbo?.dispose()
    }

    override fun keyDown(keycode: Int): Boolean {


        when (keycode) {
            Input.Keys.F1 -> {
                if (lightsType != 0) {
                    initPointLights()
                    lightsType = 0
                }
                return true
            }

            Input.Keys.F2 -> {
                if (lightsType != 1) {
                    initConeLights()
                    lightsType = 1
                }
                return true
            }

            Input.Keys.F3 -> {
                if (lightsType != 2) {
                    initChainLights()
                    lightsType = 2
                }
                return true
            }

            Input.Keys.F4 -> {
                if (lightsType != 3) {
                    initDirectionalLight()
                    lightsType = 3
                }
                return true
            }

            Input.Keys.F5 -> {
                for (light in lights) light.setColor(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1f)
                return true
            }

            Input.Keys.F6 -> {
                for (light in lights) light.distance = MathUtils.random(LIGHT_DISTANCE * 0.5f, LIGHT_DISTANCE * 2f)
                return true
            }

            Input.Keys.F7 -> {
                drawNormals = !drawNormals
                return true
            }

            Input.Keys.F9 -> {
                rayHandler?.diffuseBlendFunc?.reset()
                return true
            }

            Input.Keys.F10 -> {
                rayHandler?.diffuseBlendFunc?.set(GL20.GL_DST_COLOR, GL20.GL_SRC_COLOR)
                return true
            }

            Input.Keys.F11 -> {
                rayHandler?.diffuseBlendFunc?.set(GL20.GL_SRC_COLOR, GL20.GL_DST_COLOR)
                return true
            }

            Input.Keys.F12 -> {
                showText = !showText
                return true
            }

            else -> return false

        }
    }

    override fun mouseMoved(x: Int, y: Int): Boolean {
        testPoint[x.toFloat(), y.toFloat()] = 0f
        camera?.unproject(testPoint)
        return false
    }

    override fun scrolled(amount: Int): Boolean {
        camera?.rotate(amount.toFloat() * 3f, 0f, 0f, 1f)
        return false
    }

    companion object {
        private const val SCALE = 1f / 16f
        private const val viewportWidth = 48f
        private const val viewportHeight = 32f
        private const val RAYS_PER_BALL = 64
        private const val NUM_BALLS = 8
        private const val LIGHT_DISTANCE = 16f
        private const val RADIUS = 1f
        private const val MAX_FPS = 30
        private const val TIME_STEP = 1f / MAX_FPS
        private const val MIN_FPS = 15
        private const val MAX_STEPS = 1f + MAX_FPS.toFloat() / MIN_FPS
        private const val MAX_TIME_PER_FRAME = TIME_STEP * MAX_STEPS
        private const val VELOCITY_ITERATIONS = 6
        private const val POSITION_ITERATIONS = 2
    }

    private class DeferredObject(var diffuse: TextureRegion, var normal: TextureRegion) {
        var color = Color(Color.WHITE)
        var x = 0f
        var y = 0f
        var width = diffuse.regionWidth * SCALE
        var height = diffuse.regionHeight * SCALE
        var rotation = 0f
        var rot = false

        fun update() {
            if (rot) {
                rotation += 1f
                if (rotation > 360) rotation = 0f
            }
        }

        fun drawNormal(batch: Batch?) {
            batch?.draw(normal, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
        }

        fun draw(batch: Batch?) {
            batch?.color = color
            batch?.draw(diffuse, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
            batch?.color = Color.WHITE
        }
    }
}

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration()
    config.title = "box2d lights test"
    config.width = 800
    config.height = 480
    config.samples = 4
    config.depth = 0
    config.vSyncEnabled = true

    config.fullscreen = false
    LwjglApplication(Box2dLightCustomShaderTest(), config)
}
