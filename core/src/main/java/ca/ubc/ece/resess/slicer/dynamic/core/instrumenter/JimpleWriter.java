package ca.ubc.ece.resess.slicer.dynamic.core.instrumenter;

import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import soot.PackManager;
import soot.Scene;
import soot.options.Options;

import java.util.Arrays;

public class JimpleWriter {
    private final String outFilePath;

    public JimpleWriter(String outDir) {
        this.outFilePath = outDir + "/jimple_code/";
    }


    void initialize(String execPath) {
        AnalysisLogger.log(true, "Initializing Instrumenter");
        if (execPath.endsWith(".apk")) {
            Options.v().set_src_prec(Options.src_prec_apk);
        } else {
            Options.v().set_prepend_classpath(true);
            // Options.v().set_soot_classpath("VIRTUAL_FS_FOR_JDK");
            Options.v().set_no_bodies_for_excluded(true);
            Options.v().set_allow_phantom_refs(true);
            Options.v().set_process_dir(Arrays.asList(execPath));
            Options.v().set_output_format(Options.output_format_jimple);
            Options.v().set_output_dir(this.outFilePath);
        }
        AnalysisLogger.log(true, "Initialization done");
    }

    public void start(String execPath) {
        // soot.G.reset();
        initialize(execPath);
        Scene.v().loadNecessaryClasses();
        AnalysisLogger.log(true, "Running packs ... ");
        PackManager.v().runPacks();
        AnalysisLogger.log(true, "Writing output ... ");
        PackManager.v().writeOutput();
        AnalysisLogger.log(true, "Output written ... ");
    }
}


