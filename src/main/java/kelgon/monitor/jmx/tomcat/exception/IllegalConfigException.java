package kelgon.monitor.jmx.tomcat.exception;

public class IllegalConfigException extends Exception {

	private static final long serialVersionUID = 2463904295701472629L;

    public IllegalConfigException() {
        super();
    }

    public IllegalConfigException(String message) {
        super(message);
    }

    public IllegalConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalConfigException(Throwable cause) {
        super(cause);
    }
}
