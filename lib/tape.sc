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
            var dt_mono, tape_del_mono;
            var sat_mono, ero_lpf_freq, ero_bass_cut, filt_mono, tone_freq, tone_filt_mono, final_mono;
            var skew_lfo, skew_r, eq_var_l, eq_var_r;
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
            local_in = LocalIn.ar(1); // NÚCLEO MONO

            // Suma de entrada estéreo a mono + retroalimentación
            tape_in = ((input[0] + input[1]) * 0.5) + (local_in * fb_kr.clip(0.0, 1.2));

            // Motor Físico de Modulación (Control Rate)
            shared_wow = OnePole.kr(LFNoise2.kr(Rand(0.5, 2.0)) * wf_kr * 0.005, 0.95);
            shared_flutter = LFNoise1.kr(15) * wf_kr * 0.0005;
            shared_mod = shared_wow + shared_flutter;

            // Motor de Desgaste Magnético (Dropouts)
            shared_dust_trig = Dust.kr(ero_kr * 15);
            shared_dropout_env = Decay.kr(shared_dust_trig, 0.1);

            // Línea de Retardo MONO (Ritmo monolítico)
            dt_mono = (time_kr + shared_mod).clip(0.01, 2.0);
            tape_del_mono = DelayC.ar(tape_in, 2.0, dt_mono);

            // Saturación Magnética (Drive directo)
            sat_mono = (tape_del_mono * drive_kr).tanh;

            // Filtros Dinámicos de Erosión (Curva Parabólica para graves)
            ero_lpf_freq = LinExp.kr(ero_kr, 0.0, 1.0, 20000, 9000);
            ero_bass_cut = (ero_kr.squared) * -18.0; // Sutil al inicio, agresivo al final

            filt_mono = LPF.ar(sat_mono, ero_lpf_freq);
            filt_mono = BLowShelf.ar(filt_mono, 150, 1.0, ero_bass_cut);

            // Filtro de Tono Estático (Con Lag de 0.5s)
            tone_freq = Select.kr(tone_kr - 1,[18000, 8000, 4000, 1500]).lag(0.5);
            tone_filt_mono = LPF.ar(filt_mono, tone_freq);

            // Aplicación de Dropouts
            final_mono = tone_filt_mono * (1.0 - (shared_dropout_env * ero_kr).clip(0.0, 0.9));

            // Cierre del Bucle de Retroalimentación MONO
            LocalOut.ar(final_mono);

            // --- GENERACIÓN ESTÉREO POST-CINTA (Tape Skew & Head Variance) ---
            // L = 0ms delay. R = micro-delay caótico (0 a 1.5ms) simulando cabeceo de cinta
            skew_lfo = LFNoise2.kr(0.1).range(0.0, 0.0015);
            skew_r = DelayC.ar(final_mono, 0.01, skew_lfo);

            // Resonancia de Cabezal (Head Bump) con tolerancias de componentes analógicos
            eq_var_l = BPeakEQ.ar(final_mono, 100, 1.0, drive_kr * 3.0);
            eq_var_r = BPeakEQ.ar(skew_r, 105, 1.1, drive_kr * 3.1); // Ligeramente asimétrico

            // Protección DC Post-Bucle y Salida
            out_l = LeakDC.ar(eq_var_l);
            out_r = LeakDC.ar(eq_var_r);

            Out.ar(outBus, [out_l, out_r]);
        }).add;
    }

}
