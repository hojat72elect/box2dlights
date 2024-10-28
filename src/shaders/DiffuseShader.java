package shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * a diffuse shader is a type of shader that calculates the color of a surface based on how it interacts with light.
 * It simulates the scattering of light that occurs when light hits a matte or rough surface. This scattering
 * results in a soft, diffused reflection that makes objects appear more realistic.
 */
public class DiffuseShader {
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

        // this is always perfect precision
        final String fragmentShader = """
                #ifdef GL_ES
                precision lowp float;
                #define MED mediump
                #else
                #define MED\s
                #endif
                varying MED vec2 v_texCoords;
                uniform sampler2D u_texture;
                uniform  vec4 ambient;
                void main()
                {
                gl_FragColor.rgb = (ambient.rgb + texture2D(u_texture, v_texCoords).rgb);
                gl_FragColor.a = 1.0;
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
