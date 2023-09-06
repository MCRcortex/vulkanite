struct Vertex {
    u16vec4 position;
    u8vec4 colour;
    u16vec2 block_texture;
    u16vec2 light_texture;
    u16vec2 mid_tex_coord;
    i8vec4 tangent;
    i8vec3 normal;
    uint8_t padA__;
    i16vec2 block_id;
    i8vec3 mid_block;
    uint8_t padB__;
};

