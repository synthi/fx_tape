local fx = require("fx/lib/fx")
local mod = require("core/mods")
local hook = require("core/hook")
local tab = require("tabutil")

-- Begin post-init hack block (Estándar de inyección fx-mod)
if hook.script_post_init == nil and mod.hook.patched == nil then
    mod.hook.patched = true
    local old_register = mod.hook.register
    local post_init_hooks = {}
    mod.hook.register = function(h, name, f)
        if h == "script_post_init" then
            post_init_hooks[name] = f
        else
            old_register(h, name, f)
        end
    end
    mod.hook.register('script_pre_init', '!replace init for fake post init', function()
        local old_init = init
        init = function()
            if old_init then old_init() end -- Nil coalescing obligatorio
            for i, k in ipairs(tab.sort(post_init_hooks)) do
                local cb = post_init_hooks[k]
                print('calling: ', k)
                local ok, err = pcall(cb)
                if not ok then
                    print('hook: ' .. k .. ' failed, error: ' .. tostring(err))
                end
            end
        end
    end)
end
-- end post-init hack block

-- Declaración local absoluta (Fase 1)
local FxTape = fx:new{
    subpath = "/fx_tape"
}

-- Asignación de métodos (Fase 2)
function FxTape:add_params()
    params:add_separator("fx_tape", "fx tape")
    FxTape:add_slot("fx_tape_slot", "slot")
    
    -- Tapers: id, name, key, min, max, default, k (curve), units
    FxTape:add_taper("fx_tape_time", "time", "time", 0.01, 2.0, 0.3, 3, "s")
    FxTape:add_taper("fx_tape_feedback", "feedback", "feedback", 0.0, 1.2, 0.4, 0, "")
    FxTape:add_taper("fx_tape_wow_flutter", "wow/flutter", "wow_flutter", 0.0, 1.0, 0.1, 0, "")
    FxTape:add_taper("fx_tape_erosion", "erosion", "erosion", 0.0, 1.0, 0.0, 0, "")
    FxTape:add_taper("fx_tape_drive", "drive", "drive", 0.1, 5.0, 1.0, 0, "")
    
    -- Option: id, name, key, options_array, default_index
    FxTape:add_option("fx_tape_tone", "tone", "tone", {"18kHz", "8kHz", "4kHz", "1.5kHz"}, 1)
end

mod.hook.register("script_post_init", "fx tape mod post init", function()
    FxTape:add_params()
end)

mod.hook.register("script_post_cleanup", "tape mod post cleanup", function()
    -- Reservado para recolección de basura si fuera necesario
end)

return FxTape
