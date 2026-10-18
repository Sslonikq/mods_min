package com.explosionwand;

/** Математика для block_display: кватернионы и NBT трансформации. */
final class Disp {
    /** Кватернион из оси и угла. */
    static double[] q(double ax, double ay, double az, double ang) {
        double l = Math.sqrt(ax * ax + ay * ay + az * az); if (l < 1e-9) return new double[]{0, 0, 0, 1};
        double s = Math.sin(ang / 2) / l;
        return new double[]{ax * s, ay * s, az * s, Math.cos(ang / 2)};
    }
    static final double[] ID = {0, 0, 0, 1};

    static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    static double[] rot(double[] q, double x, double y, double z) {
        double qx = q[0], qy = q[1], qz = q[2], qw = q[3];
        double tx = 2 * (qy * z - qz * y), ty = 2 * (qz * x - qx * z), tz = 2 * (qx * y - qy * x);
        return new double[]{x + qw * tx + (qy * tz - qz * ty), y + qw * ty + (qz * tx - qx * tz), z + qw * tz + (qx * ty - qy * tx)};
    }

    /** Трансформация блока с центром (cx,cy,cz), масштабом и поворотом вокруг своего центра. */
    static String tf(double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        double[] h = rot(q, sx / 2, sy / 2, sz / 2);
        double tx = cx - h[0], ty = cy - h[1], tz = cz - h[2];
        return "transformation:{left_rotation:[" + Cmd.ff(q[0]) + "," + Cmd.ff(q[1]) + "," + Cmd.ff(q[2]) + "," + Cmd.ff(q[3]) + "]"
                + ",right_rotation:[0f,0f,0f,1f],translation:[" + Cmd.ff(tx) + "," + Cmd.ff(ty) + "," + Cmd.ff(tz) + "]"
                + ",scale:[" + Cmd.ff(sx) + "," + Cmd.ff(sy) + "," + Cmd.ff(sz) + "]}";
    }

    static String block(String id) { return "block_state:{Name:\"minecraft:" + id + "\"}"; }
}
