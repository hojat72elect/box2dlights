package tests;

import box2dLight.ChainLight;
import box2dLight.ConeLight;
import box2dLight.DirectionalLight;
import box2dLight.Light;
import box2dLight.LightData;
import box2dLight.PointLight;
import box2dLight.RayHandler;
import box2dLight.RayHandlerOptions;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.MouseJoint;
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef;
import java.util.ArrayList;

public class Box2dLightTest2 extends InputAdapter implements ApplicationListener {

    static final int RAYS_PER_BALL = 128;
    static final int NUM_BALLS = 5;
    static final float LIGHT_DISTANCE = 16f;
    static final float RADIUS = 1f;

    static final float viewportWidth = 48;
    static final float viewportHeight = 32;
    private final static int MAX_FPS = 30;
    public final static float TIME_STEP = 1f / MAX_FPS;
    private final static int MIN_FPS = 15;
    private final static float MAX_STEPS = 1f + (float) MAX_FPS / MIN_FPS;
    private final static float MAX_TIME_PER_FRAME = TIME_STEP * MAX_STEPS;
    private final static int VELOCITY_ITERATIONS = 6;
    private final static int POSITION_ITERATIONS = 2;
    OrthographicCamera camera;
    SpriteBatch batch;
    BitmapFont font;
    TextureRegion textureRegion;
    Texture bg;

    World world;

    ArrayList<Body> balls = new ArrayList<>(NUM_BALLS);

    Body groundBody;

    MouseJoint mouseJoint = null;

    Body hitBody = null;

    // pixel perfect projection which is used for font rendering
    Matrix4 normalProjection = new Matrix4();
    boolean showText = true;

    RayHandler rayHandler;
    ArrayList<Light> lights = new ArrayList<>(NUM_BALLS);
    float sunDirection = -90f;
    float physicsTimeLeft;
    long aika;
    int times;

    // we instantiate this vector and the callback here so we don't irritate the GC
    Vector3 testPoint = new Vector3();
    QueryCallback callback = new QueryCallback() {
        @Override
        public boolean reportFixture(Fixture fixture) {
            if (fixture.getBody() == groundBody)
                return true;

            if (fixture.testPoint(testPoint.x, testPoint.y)) {
                hitBody = fixture.getBody();
                return false;
            } else
                return true;
        }
    };

    //  another temporary vector
    Vector2 target = new Vector2();
    /**
     * This test introduces 4 types of lights:
     * 0 - PointLight
     * 1 - ConeLight
     * 2 - ChainLight
     * 3 - DirectionalLight
     */
    int lightsType = 0;

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        MathUtils.random.setSeed(Long.MIN_VALUE);

        camera = new OrthographicCamera(viewportWidth, viewportHeight);
        camera.position.set(0, viewportHeight / 2f, 0);
        camera.update();

        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.RED);

        textureRegion = new TextureRegion(new Texture(Gdx.files.internal("test/data/marble.png")));
        bg = new Texture(Gdx.files.internal("test/data/bg.png"));

        createPhysicsWorld();
        Gdx.input.setInputProcessor(this);

        normalProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        RayHandlerOptions options = new RayHandlerOptions();
        options.setDiffuse(true);
        options.setGammaCorrection(true);
        options.setPseudo3d(true);

        rayHandler = new RayHandler(world, options);
        rayHandler.setShadows(true);
        initPointLights();


    }

    @Override
    public void render() {

        // Rotate directional light like sun :)
        if (lightsType == 3) {
            sunDirection += Gdx.graphics.getDeltaTime() * 8f;
            float degrees = (sunDirection % 360);
            lights.get(0).setDirection(degrees);
            lights.get(0).setHeight(degrees);
            Gdx.app.debug("Degrees:", degrees + "");
        }

        camera.update();

        boolean stepped = fixedStep(Gdx.graphics.getDeltaTime());
        Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BITS | GL20.GL_BLEND_SRC_ALPHA);

        batch.setProjectionMatrix(camera.combined);
        batch.disableBlending();
        batch.begin();

        batch.draw(bg, -viewportWidth / 2f, 0, viewportWidth, viewportHeight);
        batch.enableBlending();
        for (int i = 0; i < NUM_BALLS; i++) {
            Body ball = balls.get(i);
            Vector2 position = ball.getPosition();
            float angle = MathUtils.radiansToDegrees * ball.getAngle();
            batch.draw(
                    textureRegion,
                    position.x - RADIUS, position.y - RADIUS,
                    RADIUS, RADIUS,
                    RADIUS * 2, RADIUS * 2,
                    1f, 1f,
                    angle);
        }

        batch.end();

        rayHandler.setCombinedMatrix(camera);

        if (stepped) rayHandler.update();
        rayHandler.render();


        long time = System.nanoTime();

        boolean atShadow = rayHandler.pointAtShadow(testPoint.x,
                testPoint.y);
        aika += System.nanoTime() - time;

        // Font
        if (showText) {
            batch.setProjectionMatrix(normalProjection);
            batch.begin();

            font.draw(batch, "F1 - PointLight", 0, Gdx.graphics.getHeight());
            font.draw(batch, "F2 - ConeLight", 0, Gdx.graphics.getHeight() - 15);
            font.draw(batch, "F3 - ChainLight", 0, Gdx.graphics.getHeight() - 30);
            font.draw(batch, "F4 - DirectionalLight", 0, Gdx.graphics.getHeight() - 45);
            font.draw(batch, "F5 - random lights colors", 0, Gdx.graphics.getHeight() - 75);
            font.draw(batch, "F6 - random lights distance", 0, Gdx.graphics.getHeight() - 90);
            font.draw(batch, "F9 - default blending (1.3)", 0, Gdx.graphics.getHeight() - 120);
            font.draw(batch, "F10 - over-burn blending (default in 1.2)", 0, Gdx.graphics.getHeight() - 135);
            font.draw(batch, "F11 - some other blending", 0, Gdx.graphics.getHeight() - 150);

            font.draw(batch, "F12 - toggle help text", 0, Gdx.graphics.getHeight() - 180);
            font.draw(batch, Gdx.graphics.getFramesPerSecond() + "mouse at shadows: " + atShadow + " time used for shadow calculation:" + aika / ++times + "ns", 0, 20);

            batch.end();
        }
    }

    void clearLights() {
        if (!lights.isEmpty()) {
            for (Light light : lights) {
                light.remove();
            }
            lights.clear();
        }
        groundBody.setActive(true);
    }

    void initPointLights() {
        clearLights();
        for (int i = 0; i < NUM_BALLS; i++) {
            PointLight light = new PointLight(
                    rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 0f, 0f);
            light.attachToBody(balls.get(i), RADIUS / 2f, RADIUS / 2f);
            light.setColor(
                    MathUtils.random(),
                    MathUtils.random(),
                    MathUtils.random(),
                    1f);
            light.setHeight(i + 2);
            lights.add(light);
        }
    }

    void initConeLights() {
        clearLights();
        for (int i = 0; i < NUM_BALLS; i++) {
            ConeLight light = new ConeLight(
                    rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE,
                    0, 0, 0f, MathUtils.random(15f, 40f));
            light.attachToBody(
                    balls.get(i),
                    RADIUS / 2f, RADIUS / 2f, MathUtils.random(0f, 360f));
            light.setColor(
                    MathUtils.random(),
                    MathUtils.random(),
                    MathUtils.random(),
                    1f);
            lights.add(light);
        }
    }

    void initChainLights() {
        clearLights();
        for (int i = 0; i < NUM_BALLS; i++) {
            ChainLight light = new ChainLight(
                    rayHandler, RAYS_PER_BALL, null, LIGHT_DISTANCE, 1,
                    new float[]{-5, 0, 0, 3, 5, 0});
            light.attachToBody(
                    balls.get(i),
                    MathUtils.random(0f, 360f));
            light.setColor(
                    MathUtils.random(),
                    MathUtils.random(),
                    MathUtils.random(),
                    1f);
            lights.add(light);
        }
    }

    void initDirectionalLight() {
        clearLights();

        groundBody.setActive(false);
        sunDirection = MathUtils.random(0f, 360f);

        DirectionalLight light = new DirectionalLight(rayHandler, 4 * RAYS_PER_BALL, new Color(1, 1, 1, 0.5f), sunDirection);
        light.setHeight(0);
        lights.add(light);
    }

    private boolean fixedStep(float delta) {
        physicsTimeLeft += delta;
        if (physicsTimeLeft > MAX_TIME_PER_FRAME)
            physicsTimeLeft = MAX_TIME_PER_FRAME;

        boolean stepped = false;
        while (physicsTimeLeft >= TIME_STEP) {
            world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            physicsTimeLeft -= TIME_STEP;
            stepped = true;
        }
        return stepped;
    }

    private void createPhysicsWorld() {

        world = new World(new Vector2(0, 0), true);

        float halfWidth = viewportWidth / 2f;
        ChainShape chainShape = new ChainShape();
        chainShape.createLoop(new Vector2[]{
                new Vector2(-halfWidth, 0f),
                new Vector2(halfWidth, 0f),
                new Vector2(halfWidth, viewportHeight),
                new Vector2(-halfWidth, viewportHeight)});
        BodyDef chainBodyDef = new BodyDef();
        chainBodyDef.type = BodyType.StaticBody;
        groundBody = world.createBody(chainBodyDef);
        groundBody.createFixture(chainShape, 0);
        chainShape.dispose();
        createBoxes();
    }

    private void createBoxes() {
        PolygonShape polygonShape = new PolygonShape();
        polygonShape.setAsBox(RADIUS, RADIUS);
        FixtureDef def = new FixtureDef();
        def.restitution = 0.9f;
        def.friction = 0.01f;
        def.shape = polygonShape;
        def.density = 1f;
        BodyDef boxBodyDef = new BodyDef();
        boxBodyDef.type = BodyType.DynamicBody;

        for (int i = 0; i < NUM_BALLS; i++) {
            // Create the BodyDef, set a random position above the
            // ground and create a new body
            boxBodyDef.position.x = -20 + (float) (Math.random() * 40);
            boxBodyDef.position.y = 10 + (float) (Math.random() * 15);
            Body boxBody = world.createBody(boxBodyDef);
            boxBody.createFixture(def).setUserData(new LightData(1, true));
            balls.add(boxBody);
        }
        polygonShape.dispose();
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int newParam) {
        // translate the mouse coordinates to world coordinates
        testPoint.set(x, y, 0);
        camera.unproject(testPoint);

        // ask the world which bodies are within the given
        // bounding box around the mouse pointer
        hitBody = null;
        world.QueryAABB(callback, testPoint.x - 0.1f, testPoint.y - 0.1f,
                testPoint.x + 0.1f, testPoint.y + 0.1f);

        // if we hit something we create a new mouse joint
        // and attach it to the hit body.
        if (hitBody != null) {
            MouseJointDef def = new MouseJointDef();
            def.bodyA = groundBody;
            def.bodyB = hitBody;
            def.collideConnected = true;
            def.target.set(testPoint.x, testPoint.y);
            def.maxForce = 1000.0f * hitBody.getMass();

            mouseJoint = (MouseJoint) world.createJoint(def);
            hitBody.setAwake(true);
        }

        return false;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        camera.unproject(testPoint.set(x, y, 0));
        target.set(testPoint.x, testPoint.y);
        // if a mouse joint exists we simply update
        // the target of the joint based on the new
        // mouse coordinates
        if (mouseJoint != null) mouseJoint.setTarget(target);
        return false;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        // if a mouse joint exists we simply destroy it
        if (mouseJoint != null) {
            world.destroyJoint(mouseJoint);
            mouseJoint = null;
        }
        return false;
    }

    @Override
    public void dispose() {
        rayHandler.dispose();
        world.dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {

            case Input.Keys.F1:
                if (lightsType != 0) {
                    initPointLights();
                    lightsType = 0;
                }
                return true;

            case Input.Keys.F2:
                if (lightsType != 1) {
                    initConeLights();
                    lightsType = 1;
                }
                return true;

            case Input.Keys.F3:
                if (lightsType != 2) {
                    initChainLights();
                    lightsType = 2;
                }
                return true;

            case Input.Keys.F4:
                if (lightsType != 3) {
                    initDirectionalLight();
                    lightsType = 3;
                }
                return true;

            case Input.Keys.F5:
                for (Light light : lights)
                    light.setColor(
                            MathUtils.random(),
                            MathUtils.random(),
                            MathUtils.random(),
                            1f);
                return true;

            case Input.Keys.F6:
                for (Light light : lights)
                    light.setDistance(MathUtils.random(
                            LIGHT_DISTANCE * 0.5f, LIGHT_DISTANCE * 2f));
                return true;

            case Input.Keys.F9:
                rayHandler.diffuseBlendFunc.reset();
                return true;

            case Input.Keys.F10:
                rayHandler.diffuseBlendFunc.set(
                        GL20.GL_DST_COLOR, GL20.GL_SRC_COLOR);
                return true;

            case Input.Keys.F11:
                rayHandler.diffuseBlendFunc.set(
                        GL20.GL_SRC_COLOR, GL20.GL_DST_COLOR);
                return true;

            case Input.Keys.F12:
                showText = !showText;
                return true;

            default:
                return false;

        }
    }

    @Override
    public boolean mouseMoved(int x, int y) {
        testPoint.set(x, y, 0);
        camera.unproject(testPoint);
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        camera.rotate((float) amount * 3f, 0, 0, 1);
        return false;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resize(int arg0, int arg1) {
    }

    @Override
    public void resume() {
    }

}
