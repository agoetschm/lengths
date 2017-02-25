package ch.goetschy.android.lengths;

public class Engine {
    private static final double C = 0.936; // eye height constant

    private double mDistA = 0.0;
    private double mDistB = 0.0;
    private double mDistAtoB = 0.0;

    private double H = 1.75; // height

    public void setHeight(double h) {
        H = h;
    }

    public double getHeight() {
        return H;
    }

    public double setAlpha(double alpha) { // radian
        double camHeight = H * C;
        mDistA = Math.sqrt(Math.pow(1 / Math.cos(alpha), 2) - 1) * camHeight;

        return mDistA;
    }

    public double setBeta(double beta) {
        double camHeight = H * C;
        mDistB = Math.sqrt(Math.pow(1 / Math.cos(beta), 2) - 1) * camHeight;

        return mDistB;
    }

    public double setGamma(double gamma) {
        mDistAtoB = Math.sqrt(Math.pow(mDistA, 2) + Math.pow(mDistB, 2) - 2
                * mDistA * mDistB * Math.cos(gamma));
        return mDistAtoB;
    }

    public double getDistA() {
        return round(mDistA);
    }

    public double getDistB() {
        return round(mDistB);
    }

    public double getDistAtoB() {
        return round(mDistAtoB);
    }

    // 2 digits
    private double round(double d) {
        return Math.round(d * 100) / 100.0;
    }

}
