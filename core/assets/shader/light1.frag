#ifdef GL_ES
    precision highp float;
#endif

float falloff = 0.3; //0.3;
float damper  = 0.1;

float brightness(float maxDistance, vec2 coords, vec2 light)
{
    float dist = (distance(coords, light));
    if (dist > maxDistance*10)
    	return 0;
    float brightness = falloff/((dist+(maxDistance*damper) )/maxDistance);
    return brightness;
}

float brightness2 (float intensity, vec2 pixelCoords, vec2 lightCoords) 
{
	return 1.0/(distance(pixelCoords, lightCoords));
}


struct LightSource
{
        vec2 position;
        vec4 color;
        float intensity;
};


uniform mat4 inverseProjectionMatrix; 
in vec2 texCoord;

varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform vec3 worldPos;
uniform LightSource lights[32];
uniform int totalLights;
   
void main() {
    vec4 worldSpacePositionOfScreenFragment = inverseProjectionMatrix * vec4(v_texCoords.xy * 2.0 - 1.0, 0.0, 1.0);
    vec3 processingPosition = vec3(worldSpacePositionOfScreenFragment.xyz/worldSpacePositionOfScreenFragment.w)  ;
    
    gl_FragColor = vec4(0.0,0.0,0.0,1);
	for ( int i = 0; i < totalLights; i++ ) {
		gl_FragColor += lights[i].color * brightness(lights[i].intensity, vec2(processingPosition.xy) - worldPos.xy, lights[i].position);
	}	
   	gl_FragColor *= v_color * texture2D(u_texture, v_texCoords);
    
}