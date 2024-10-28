package shaders;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;


public final class WithoutShadowShader {
    static public ShaderProgram createShadowShader() {
        final String vertexShader = """
                attribute vec4 a_position;
                attribute vec2 a_texCoord;
                varying vec2 v_texCoords;
                
                void main()
                {
                   v_texCoords = a_texCoord;
                   gl_Position = a_position;
                }
                """;

        final String fragmentShader = """
                #ifdef GL_ES
                precision lowp float;
                #define MED mediump
                #else
                #define MED\s
                #endif
                varying MED vec2 v_texCoords;
                uniform sampler2D u_texture;
                void main()
                {
                gl_FragColor = texture2D(u_texture, v_texCoords);
                }
                """;
        ShaderProgram.pedantic = false;
        ShaderProgram woShadowShader = new ShaderProgram(
                vertexShader,
                fragmentShader
        );
        if (!woShadowShader.isCompiled()) {
            woShadowShader = new ShaderProgram(
                    "#version 330 core\n" + vertexShader,
                    "#version 330 core\n" + fragmentShader
            );
            if (!woShadowShader.isCompiled()) {
                Gdx.app.log("ERROR", woShadowShader.getLog());
            }
        }

        return woShadowShader;
    }
}
