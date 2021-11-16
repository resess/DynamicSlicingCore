package ca.ubc.ece.resess.slicer.dynamic.core.exceptions;

public class TraceFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TraceFileException() {
        super("Exception parsing trace file", new Throwable());
    }
}
