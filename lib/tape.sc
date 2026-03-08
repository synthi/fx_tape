FxTape : FxBase {

    *new { 
        var ret = super.newCopyArgs(nil, \none, (
            time: 0.3,
            feedback: 0.4,
            wow_flutter: 0.1,
            erosion: 0.0,
            drive: 1.0,
            tone: 1
        ), nil, 0.5);
        ^ret;
    }

    *initClass {
        FxSetup.register(this.new);
    }

    subPath {
        ^"/fx_tape";
    }  

    symbol {
        ^\fxTape;
    }

    addSynthdefs {
        SynthDef(\fxTape, { |inBus, outBus|
            // 1. DECLARACIÓN ABSOLUTA DE VARIABLES (Fase 1)
            var input, local_in, tape_in;
            var shared_wow, shared_flutter, shared_mod;
            var shared_dust_trig, shared_dropout_env;
            var dt_l, dt_r, tape_del_l, tape_del_r;
            var head_bump_l, head_bump_r;
            var comp_gain, sat_l, sat_r;
            var ero_lpf_freq, ero_bass_cut;
            var filt_l, filt_r;
            var tone_freq, tone_filt_l, tone_filt_r;
            var final_l, final_r;
            var out_l, out_r;
            var time_kr, fb_kr, wf_kr, ero_kr, drive_kr, tone_kr;

            // 2. ASIGNACIÓN Y OPERACIÓN (Fase 2)
            time_kr = \time.kr(0.3).lag(0.1);
            fb_kr = \feedback.kr(0.4).lag(0.1);
            wf_kr = \wow_flutter.kr(0.1).lag(0.1);
            ero_kr = \erosion.kr(0.0).lag(0.1);
            drive_kr = \drive.kr(1.0).lag(0.1);
            tone_kr = \tone.kr(1);

            input = In.ar(inBus, 2);
            local_in = LocalIn.ar(2);

            // Suma de entrada con retroalimentación (Permite auto-oscilación hasta 1.2)
            tape_in = input + (local_in * fb_kr.clip(0.0, 1.2));

            // Motor Físico de Modulación (Control Rate)
            shared_wow = OnePole.kr(LFNoise2.kr(Rand(0.5, 2.0)) * wf_kr * 0.005, 0.95);
            shared_flutter = LFNoise1.kr(15) * wf_kr * 0.0005;
            shared_mod = shared_wow + shared_flutter;

            // Motor de Desgaste Magnético (Dropouts)
            shared_dust_trig = Dust.kr(ero_kr * 15);
            shared_dropout_env = Decay.kr(shared_dust_trig, 0.1);

            // Líneas de Retardo Asimétricas (Efecto Haas)
            dt_l = (time_kr + shared_mod).clip(0.01, 2.0);
            dt_r = ((time_kr * 1.02) + 0.005 + shared_mod).clip(0.01, 2.0);

            tape_del_l = DelayC.ar(tape_in[0], 2.0, dt_l);
            tape_del_r = DelayC.ar(tape_in[1], 2.0, dt_r);

            // Saturación Magnética (Drive directo, sin comp_gain destructivo)
            sat_l = (tape_del_l * drive_kr).tanh;
            sat_r = (tape_del_r * drive_kr).tanh;

            // Filtros Dinámicos de Erosión (Corrección Matemática de Colapso)
            ero_lpf_freq = LinExp.kr(ero_kr, 0.0, 1.0, 20000, 9000);
            ero_bass_cut = LinLin.kr(ero_kr, 0.0, 1.0, 0.0, -18.0);

            filt_l = LPF.ar(sat_l, ero_lpf_freq);
            filt_r = LPF.ar(sat_r, ero_lpf_freq);
            
            filt_l = BLowShelf.ar(filt_l, 150, 1.0, ero_bass_cut);
            filt_r = BLowShelf.ar(filt_r, 150, 1.0, ero_bass_cut);

            // Filtro de Tono Estático (Con Lag de 0.5s para evitar Zipper Noise)
            tone_freq = Select.kr(tone_kr - 1,[18000, 8000, 4000, 1500]).lag(0.5);
            tone_filt_l = LPF.ar(filt_l, tone_freq);
            tone_filt_r = LPF.ar(filt_r, tone_freq);

            // Aplicación de Dropouts (Pérdida de volumen microscópica)
            final_l = tone_filt_l * (1.0 - (shared_dropout_env * ero_kr).clip(0.0, 0.9));
            final_r = tone_filt_r * (1.0 - (shared_dropout_env * ero_kr).clip(0.0, 0.9));

            // Cierre del Bucle de Retroalimentación (SIN Head Bump para evitar resonancia infinita)
            LocalOut.ar([final_l, final_r]);

            // Resonancia de Cabezal (Head Bump) aplicada SOLO a la salida de escucha
            out_l = BPeakEQ.ar(final_l, 100, 1.0, drive_kr * 3.0);
            out_r = BPeakEQ.ar(final_r, 100, 1.0, drive_kr * 3.0);

            // Protección DC Post-Bucle y Salida
            out_l = LeakDC.ar(out_l);
            out_r = LeakDC.ar(out_r);

            Out.ar(outBus,[out_l, out_r]);
        }).add;
    }

}
