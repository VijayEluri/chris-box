package org.esa.beam.chris.operators;

import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.DateTimeUtils;

import java.awt.geom.Point2D;
import java.util.Calendar;
import java.util.Date;

import static org.esa.beam.chris.util.math.internal.Pow.pow2;
import static org.esa.beam.chris.util.math.internal.Pow.pow3;

class Conversions {

    /**
     * Major radius of WGS-84 ellipsoid (m).
     */
    private static final double WGS84_A = 6378137.0;
    /**
     * Flattening of WGS-84 ellipsoid.
     */
    private static final double WGS84_F = 1.0 / 298.257223563;
    /**
     * Eccentricity squared.
     */
    private static final double WGS84_E = WGS84_F * (2.0 - WGS84_F);

    /**
     * The epoch (millis) for the Modified Julian Date (MJD) which
     * corresponds to 1858-11-17 00:00.
     */
    private static final long EPOCH_MJD = -3506716800000L;
    
    private static final double MJD_TO_JD_OFFSET = 2400000.5;
    private static final double MILLIS_PER_DAY = 86400000.0;
    public static final double SECONDS_PER_DAY = 86400.0;

    private Conversions() {
    }

    /**
     * Returns the Julian Date (JD) corresponding to a date.
     *
     * @param date the date.
     *
     * @return the JD corresponding to the date.
     */
    public static double dateToJD(Date date) {
        return dateToMJD(date) + MJD_TO_JD_OFFSET;
    }

    /**
     * Returns the Modified Julian Date (MJD) corresponding to a date.
     *
     * @param date the date.
     *
     * @return the MJD corresponding to the date.
     */
    public static double dateToMJD(Date date) {
        return (date.getTime() - EPOCH_MJD) / MILLIS_PER_DAY;
    }

    /**
     * Returns the date corresponding to a Modified Julian Date (MJD).
     *
     * @param mjd the MJD.
     *
     * @return the date corresponding to the MJD.
     */
    static Date mjdToDate(double mjd) {
        return new Date(EPOCH_MJD + (long) (mjd * MILLIS_PER_DAY));
    }

    /**
     * Converts UT1 into Greenwich Mean Sidereal Time (GST, IAU 1982 model).
     * <p/>
     * Note that the unit of GST is radian (rad).
     *
     * @param mjd the UT1 expressed as Modified Julian Date (MJD).
     *
     * @return the GST corresponding to the MJD given.
     */
    public static double mjdToGST(double mjd) {
        // radians per sidereal second
        final double secRad = 7.272205216643039903848712E-5;

        // seconds per day, days per Julian century
        final double daySec = 86400.0;
        final double cenDay = 36525.0;

        // reference epoch (J2000)
        final double mjd0 = 51544.5;

        // coefficients of IAU 1982 GMST-UT1 model
        final double a = 24110.54841;
        final double b = 8640184.812866;
        final double c = 0.093104;
        final double d = 6.2E-6;

        final double mjd1 = Math.floor(mjd);
        final double mjd2 = mjd - mjd1;

        // Julian centuries since epoch
        final double t = (mjd2 + (mjd1 - mjd0)) / cenDay;
        // fractional part of MJD(UT1) in seconds
        final double f = daySec * mjd2;

        final double twoPi = 2.0 * Math.PI;
        final double gst = (secRad * ((a + (b + (c - d * t) * t) * t) + f)) % twoPi;

        if (gst < 0.0) {
            return gst + twoPi;
        }

        return gst;
    }

    /**
     * Returns the date corresponding to a Julian Date (JD).
     *
     * @param jd the JD.
     *
     * @return the date corresponding to the JD.
     */
    static Date jdToDate(double jd) {
        return mjdToDate(jdToMJD(jd));
    }

    /**
     * Returns the Julian Date (JD) corresponding to a Modified Julian Date (JD).
     *
     * @param mjd the MJD.
     *
     * @return the JD corresponding to the MJD.
     */
    public static double mjdToJD(double mjd) {
        return mjd + MJD_TO_JD_OFFSET;
    }

    /**
     * Converts UT1 into Greenwich Mean Sidereal Time (GST, IAU 1982 model).
     * <p/>
     * Note that the unit of GST is radian (rad).
     *
     * @param jd the UT1 expressed as Julian Date (JD).
     *
     * @return the GST corresponding to the JD given.
     */
    public static double jdToGST(double jd) {
        return mjdToGST(jdToMJD(jd));
    }

    /**
     * Returns the Modified Julian Date (MJD) corresponding to a Julian Date (JD).
     *
     * @param jd the JD.
     *
     * @return the MJD corresponding to the JD.
     */
    public static double jdToMJD(double jd) {
        return jd - MJD_TO_JD_OFFSET;
    }

    public static void wgsToEcef(double lon, double lat, double alt, double[] ecef) {
        if (Math.abs(lat) > 90.0) {
            throw new IllegalArgumentException("|lat| > 90.0");
        }
        if (ecef == null) {
            throw new IllegalArgumentException("ecef == null");
        }
        if (ecef.length != 3) {
            throw new IllegalArgumentException("ecef.length != 3");
        }

        final double u = Math.toRadians(lon);
        final double v = Math.toRadians(lat);

        final double cu = Math.cos(u);
        final double su = Math.sin(u);
        final double cv = Math.cos(v);
        final double sv = Math.sin(v);

        final double a = WGS84_A / Math.sqrt(1.0 - WGS84_E * pow2(sv));
        final double b = (a + alt) * cv;

        ecef[0] = b * cu;
        ecef[1] = b * su;
        ecef[2] = ((1.0 - WGS84_E) * a + alt) * sv;
    }
    
    /**
     * Transforms geodetic coordinates (spherical) to Earth Centered Fixed ECF
     * cartesian coordinates (rectangular) Altitude h starts at the reference
     * geoid and must be in kilometers, Latitude and Longitude are operated in degrees.
     * 
     * NOTE: accuracy might be improved for coordinates close to the XY plane or
     * the Z axis by using alternative trigonometric formulation in those cases
     * 
     */
    public static Point2D ecef2wgs(double x, double y, double z) {
        double b = WGS84_A * (1.0 - WGS84_F);
        b = b * Math.signum(z);

        double R = Math.sqrt(pow2(x) + pow2(y));
        double E = (b*z - (pow2(WGS84_A) - pow2(b)))/(WGS84_A*R);
        double F = (b*z + (pow2(WGS84_A) - pow2(b)))/(WGS84_A*R);
        double P = 4*(E*F + 1)/3.0;
        double Q = 2*(pow2(E) - pow2(F));
        double D = Math.sqrt(pow3(P) + pow2(Q));
        double nu = Math.pow((D-Q), (1.0/3.0)) - Math.pow((D+Q), (1.0/3.0));
        double G = (Math.sqrt(pow2(E)+nu) + E)/2.0;
        double t = Math.sqrt(pow2(G) + (F - nu*G)/(2*G - E)) - G;

        double LATgdt = Math.atan2(WGS84_A * (1 - pow2(t)), 2*b*t);
        double LONgdt = Math.atan2(y, x);
        double ALTgdt = (R - WGS84_A*t) * Math.cos(LATgdt) + (z - b) * Math.sin(LATgdt);

        if (x == 0.0 && y == 0.0 && z != 0.0) {
            LATgdt = Math.PI/2.0 * Math.signum(z);
            LONgdt = 0.0;
            ALTgdt = Math.abs(z - b);
        }
        if (z == 0.0) {
            LATgdt = 0.0;
            ALTgdt = R - WGS84_A;
        }

        return new Point2D.Double(Math.toDegrees(LONgdt), Math.toDegrees(LATgdt));
    }

    /**
     * Calculates the Julian Date (JD) from the given parameters.
     *
     * @param year  the value used to set the <code>YEAR</code> calendar field.
     * @param month the value used to set the <code>MONTH</code> calendar field.
     *              Month value is 0-based. E.g., 0 for January.
     * @param day   the value used to set the <code>DAY_OF_MONTH</code> calendar field.
     *
     * @return the Julian Date.
     */
    public static double julianDate(int year, int month, int day) {
        return julianDate(year, month, day, 0, 0, 0);
    }

    /**
     * Calculates the Julian Date (JD) from the given parameter.
     *
     * @param year      the value used to set the <code>YEAR</code> calendar field.
     * @param month     the value used to set the <code>MONTH</code> calendar field.
     *                  Month value is 0-based. E.g., 0 for January.
     * @param day       the value used to set the <code>DAY_OF_MONTH</code> calendar field.
     * @param hourOfDay the value used to set the <code>HOUR_OF_DAY</code> calendar field.
     * @param minute    the value used to set the <code>MINUTE</code> calendar field.
     * @param second    the value used to set the <code>SECOND</code> calendar field.
     *
     * @return the Julian Date.
     */
    public static double julianDate(int year, int month, int day, int hourOfDay, int minute, int second) {
        final Calendar calendar = ProductData.UTC.createCalendar();
        calendar.set(year, month, day, hourOfDay, minute, second);
        final Date date = calendar.getTime();

        return DateTimeUtils.utcToJD(date);
    }
}
