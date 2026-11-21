package me.bendy.moonfragments.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * A skirt drawn as one connected, bendable surface instead of rigid boxes.
 * <p>
 * The skirt is a stack of {@link Band}s (vertical tubes with a rectangular cross-section) joined by flat ledges where a
 * band is wider than the one above. Every frame each vertex is turned about the hip line (the axis the legs swing on)
 * by an angle blended from the rear leg's swing (back edge) to the forward leg's swing (front edge), based on how far
 * forward the vertex sits. The front and back follow the legs exactly, so the legs stay inside, and the sides fan
 * smoothly between them, so the skirt never splits open.
 * <p>
 * {@link ModelPart} is final, so the mesh lives in a pool of blank cubes whose polygon slots are rewritten each frame;
 * unused slots get a zero-size polygon that draws nothing.
 */
public final class SkirtMesh {
    /** Legs (and the skirt) pivot on this line, in body pixels. */
    public static final float HIP_Y = 12.0F;

    /**
     * A vertical band of the skirt from {@code yTop} to {@code yBottom} (body pixels), half-width {@code hx}, half-depth
     * {@code hz}. {@code pleat} zig-zags the outline (0 for none). UVs use a standard box layout at {@code (u, v)} with
     * size {@code 2hx x (yBottom-yTop) x 2hz}; a ledge above this band uses the box's top-face area.
     */
    public record Band(float yTop, float yBottom, float hx, float hz, float pleat, int u, int v) {
    }

    /**
     * The whole skirt. If {@code startHx > 0}, a ledge joins a fixed-size ring ({@code startHx}, {@code startHz}) at the
     * first band's top to the first band (e.g. the mini skirt's flare out from its upper tier).
     */
    public record Spec(float startHx, float startHz, List<Band> bands, int frontSegments, int sideSegments,
            int textureWidth, int textureHeight) {
    }

    private static final ModelPart.Polygon EMPTY = new ModelPart.Polygon(new ModelPart.Vertex[] {
            new ModelPart.Vertex(0, 0, 0, 0, 0), new ModelPart.Vertex(0, 0, 0, 0, 0),
            new ModelPart.Vertex(0, 0, 0, 0, 0), new ModelPart.Vertex(0, 0, 0, 0, 0)}, new Vector3f(0, 1, 0));

    private final Spec spec;
    private final List<ModelPart.Cube> cubes = new ArrayList<>();
    private final ModelPart part;
    private int slot;

    public SkirtMesh(Spec spec) {
        this.spec = spec;
        int segments = 2 * spec.frontSegments() + 2 * spec.sideSegments();
        int strips = spec.bands().size() * 2 + 1; // each band, plus at most one ledge above each
        int cubesNeeded = (segments * strips + 5) / 6;
        for (int i = 0; i < cubesNeeded; i++) {
            cubes.add(new ModelPart.Cube(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, EnumSet.allOf(Direction.class)));
        }
        this.part = new ModelPart(cubes, Map.of());
    }

    public ModelPart part() {
        return part;
    }

    /**
     * Rebuilds the mesh for this frame.
     * @param front swing of the forward-most leg (negative = forward), radians
     * @param back swing of the rear-most leg (positive = back), radians
     */
    public void update(float front, float back) {
        slot = 0;
        int nF = spec.frontSegments();
        int nS = spec.sideSegments();
        float[][] previousBottom = null;
        Band previous = null;
        for (int b = 0; b < spec.bands().size(); b++) {
            Band band = spec.bands().get(b);
            float[][] top = ring(band.hx(), band.hz(), band.pleat(), nF, nS);
            float[][] bottom = top; // same outline top and bottom
            // Ledge from the ring above (previous band's bottom, or the fixed start ring) out to this band.
            float[][] inner = null;
            float innerHz = 0;
            if (previous != null && (previous.hx() != band.hx() || previous.hz() != band.hz())) {
                inner = previousBottom;
                innerHz = previous.hz();
            } else if (previous == null && spec.startHx() > 0) {
                inner = ring(spec.startHx(), spec.startHz(), 0, nF, nS);
                innerHz = spec.startHz();
            }
            if (inner != null) {
                addLedge(inner, innerHz, top, band, front, back);
            }
            addBand(top, bottom, band, front, back);
            previous = band;
            previousBottom = bottom;
        }
        // Blank out the rest of the pool.
        int total = cubes.size() * 6;
        while (slot < total) {
            put(EMPTY);
        }
    }

    /** Outline points (x, z, faceIndex) around a rectangle, starting front-left, going round via +x, back, -x. */
    private static float[][] ring(float hx, float hz, float pleat, int nF, int nS) {
        float[][] points = new float[2 * nF + 2 * nS][];
        int k = 0;
        for (int j = 0; j < nF; j++) { // front (north, -z)
            float off = (j % 2 == 1) ? pleat : 0;
            points[k++] = new float[] {-hx + 2 * hx * j / nF, -hz - off, 0};
        }
        for (int j = 0; j < nS; j++) { // left of the wearer (east, +x)
            float off = (j % 2 == 1) ? pleat : 0;
            points[k++] = new float[] {hx + off, -hz + 2 * hz * j / nS, 1};
        }
        for (int j = 0; j < nF; j++) { // back (south, +z)
            float off = (j % 2 == 1) ? pleat : 0;
            points[k++] = new float[] {hx - 2 * hx * j / nF, hz + off, 2};
        }
        for (int j = 0; j < nS; j++) { // right of the wearer (west, -x)
            float off = (j % 2 == 1) ? pleat : 0;
            points[k++] = new float[] {-hx - off, hz - 2 * hz * j / nS, 3};
        }
        return points;
    }

    private void addBand(float[][] top, float[][] bottom, Band band, float front, float back) {
        float w = 2 * band.hx();
        float d = 2 * band.hz();
        float h = band.yBottom() - band.yTop();
        int n = top.length;
        for (int k = 0; k < n; k++) {
            float[] a = top[k];
            float[] c = top[(k + 1) % n];
            float[] cb = bottom[(k + 1) % n];
            float[] ab = bottom[k];
            int face = (int) a[2];
            float[][] verts = {
                    {a[0], band.yTop(), a[1]}, {c[0], band.yTop(), c[1]},
                    {cb[0], band.yBottom(), cb[1]}, {ab[0], band.yBottom(), ab[1]}};
            float[][] uv = new float[4][];
            for (int i = 0; i < 4; i++) {
                float x = verts[i][0];
                float z = verts[i][2];
                // For the shared corner point, use the edge this segment belongs to.
                float u = switch (face) {
                    case 0 -> band.u() + d + Mth.clamp(x + band.hx(), 0, w);
                    case 1 -> band.u() + d + w + Mth.clamp(z + band.hz(), 0, d);
                    case 2 -> band.u() + 2 * d + w + Mth.clamp(band.hx() - x, 0, w);
                    default -> band.u() + Mth.clamp(band.hz() - z, 0, d);
                };
                float v = band.v() + d + (i < 2 ? 0 : h);
                uv[i] = new float[] {u, v};
            }
            Vector3f outward = switch (face) {
                case 0 -> new Vector3f(0, 0, -1);
                case 1 -> new Vector3f(1, 0, 0);
                case 2 -> new Vector3f(0, 0, 1);
                default -> new Vector3f(-1, 0, 0);
            };
            float hz = band.hz();
            emitQuad(verts, uv, new float[] {hz, hz, hz, hz}, outward, front, back);
        }
    }

    private void addLedge(float[][] inner, float innerHz, float[][] outer, Band band, float front, float back) {
        float w = 2 * band.hx();
        float d = 2 * band.hz();
        float y = band.yTop();
        int n = outer.length;
        for (int k = 0; k < n; k++) {
            float[] i0 = inner[k];
            float[] i1 = inner[(k + 1) % n];
            float[] o1 = outer[(k + 1) % n];
            float[] o0 = outer[k];
            float[][] verts = {{i0[0], y, i0[1]}, {i1[0], y, i1[1]}, {o1[0], y, o1[1]}, {o0[0], y, o0[1]}};
            float[][] uv = new float[4][];
            for (int i = 0; i < 4; i++) {
                float u = band.u() + d + Mth.clamp(verts[i][0] + band.hx(), 0, w);
                float v = band.v() + Mth.clamp(band.hz() - verts[i][2], 0, d);
                uv[i] = new float[] {u, v};
            }
            // The two rings have different depths; blend by each vertex's own ring. Model y points down, so up is -y.
            float[] hz = {innerHz, innerHz, band.hz(), band.hz()};
            emitQuad(verts, uv, hz, new Vector3f(0, -1, 0), front, back);
        }
    }

    /**
     * Turns the four rest-pose corners about the hip and writes the quad into the next polygon slot.
     * @param hz each corner's ring half-depth, used to blend between the back and front swing
     * @param outward the quad's rest-pose outward direction, used to orient the normal
     */
    private void emitQuad(float[][] verts, float[][] uv, float[] hz, Vector3f outward, float front, float back) {
        Vector3f[] p = new Vector3f[4];
        float angleSum = 0;
        for (int i = 0; i < 4; i++) {
            float angle = angleAt(verts[i][2], hz[i], front, back);
            angleSum += angle;
            p[i] = turn(verts[i][0], verts[i][1], verts[i][2], angle);
        }
        // Normal from the deformed quad, pointing the same way as the rest-pose outward direction turned with it.
        Vector3f normal = new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[1]));
        Vector3f expected = turnDirection(outward, angleSum / 4);
        if (normal.lengthSquared() < 1.0E-8F) {
            normal.set(expected);
        } else {
            normal.normalize();
            if (normal.dot(expected) < 0) {
                normal.negate();
            }
        }
        ModelPart.Vertex[] vertices = new ModelPart.Vertex[4];
        for (int i = 0; i < 4; i++) {
            vertices[i] = new ModelPart.Vertex(p[i].x, p[i].y, p[i].z,
                    uv[i][0] / spec.textureWidth(), uv[i][1] / spec.textureHeight());
        }
        put(new ModelPart.Polygon(vertices, normal));
    }

    /** Back edge (z = +hz) follows {@code back}, front edge (z = -hz) follows {@code front}, blended in between. */
    private static float angleAt(float z, float hz, float front, float back) {
        float n = Mth.clamp(z / hz, -1.0F, 1.0F);
        float t = (1.0F - n) * 0.5F; // 0 at the back, 1 at the front
        return back + (front - back) * t;
    }

    /** Rotates a body-space point about the hip line (x axis through y = HIP_Y, z = 0). */
    private static Vector3f turn(float x, float y, float z, float angle) {
        float dy = y - HIP_Y;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        return new Vector3f(x, HIP_Y + dy * cos - z * sin, dy * sin + z * cos);
    }

    private static Vector3f turnDirection(Vector3f dir, float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        return new Vector3f(dir.x, dir.y * cos - dir.z * sin, dir.y * sin + dir.z * cos);
    }

    private void put(ModelPart.Polygon polygon) {
        cubes.get(slot / 6).polygons[slot % 6] = polygon;
        slot++;
    }
}
