package shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public final class ShadowShader {
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
                uniform vec4 ambient;
                void main()
                {
                vec4 c = texture2D(u_texture, v_texCoords);
                gl_FragColor.rgb = c.rgb * c.a + ambient.rgb;
                gl_FragColor.a = ambient.a - c.a;
                }
                """;
        ShaderProgram.pedantic = false;
        ShaderProgram shadowShader = new ShaderProgram(
                vertexShader,
                fragmentShader
        );
        if (!shadowShader.isCompiled()) {
            shadowShader = new ShaderProgram(
                    "#version 330 core\n" + vertexShader,
                    "#version 330 core\n" + fragmentShader
            );
            if (!shadowShader.isCompiled()) {
                Gdx.app.log("ERROR", shadowShader.getLog());
            }
        }

        return shadowShader;
    }
}
