package me.bendy.moonfragments.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
 * The rest-pose quads (corners, UVs, ring depths, outward directions) are computed once; each frame only turns the
 * corners, and nothing is recomputed while the leg angles are unchanged. {@link ModelPart} is final, so the mesh is
 * drawn by a {@link ModelPart.Cube} subclass whose {@code compile} writes the quads itself. Overriding {@code compile}
 * (rather than rewriting a cube's polygon slots) matters for Sodium and its forks: they patch {@code Cube.compile} to
 * draw every cube from the box size it was constructed with, ignoring its polygons. An override never runs that
 * patched code.
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
     * first band's top to the first band (e.g. closing the gap between a bodice or upper tier and a wider skirt).
     */
    public record Spec(float startHx, float startHz, List<Band> bands, int frontSegments, int sideSegments,
            int textureWidth, int textureHeight) {
    }

    /**
     * One rest-pose quad: corners {@code (x, y, z)} in body pixels, normalized UVs, each corner's ring half-depth (for
     * blending the swing), and its outward direction (to orient the normal).
     */
    private record Quad(float[][] corners, float[][] uvs, float[] hz, Vector3f outward) {
    }

    /** Model space has y pointing down, so "up" is -y. */
    private static final Vector3f UP = new Vector3f(0, -1, 0);
    private static final Vector3f[] FACE_OUTWARD = {
            new Vector3f(0, 0, -1), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0)};

    private final List<Quad> quads;
    private final ModelPart part;
    private float lastFront = Float.NaN;
    private float lastBack = Float.NaN;

    /** Current (turned) corner positions in block units: quad * 12 + corner * 3 + axis. */
    private final float[] positions;
    /** Current normal per quad: quad * 3 + axis. */
    private final float[] normals;

    // Per-frame scratch, reused to avoid allocation.
    private final Vector3f[] turned = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f normal = new Vector3f();
    private final Vector3f edgeB = new Vector3f();
    private final Vector3f expected = new Vector3f();

    public SkirtMesh(Spec spec) {
        this.quads = buildQuads(spec);
        this.positions = new float[quads.size() * 12];
        this.normals = new float[quads.size() * 3];
        this.part = new ModelPart(List.of(new SkirtCube()), Map.of());
        update(0.0F, 0.0F);
    }

    public ModelPart part() {
        return part;
    }

    /**
     * Updates the mesh for this frame. Does nothing if the leg angles haven't changed since the last call.
     * @param front swing of the forward-most leg (negative = forward), radians
     * @param back swing of the rear-most leg (positive = back), radians
     */
    public void update(float front, float back) {
        if (front == lastFront && back == lastBack) {
            return;
        }
        lastFront = front;
        lastBack = back;
        for (int q = 0; q < quads.size(); q++) {
            turnQuad(q, quads.get(q), front, back);
        }
    }

    private void turnQuad(int q, Quad quad, float front, float back) {
        float angleSum = 0;
        for (int i = 0; i < 4; i++) {
            float[] c = quad.corners()[i];
            float angle = angleAt(c[2], quad.hz()[i], front, back);
            angleSum += angle;
            turn(c[0], c[1], c[2], angle, turned[i]);
        }
        // Normal from the turned quad, pointing the same way as the rest-pose outward direction turned with it.
        normal.set(turned[2]).sub(turned[0]).cross(edgeB.set(turned[3]).sub(turned[1]));
        turnDirection(quad.outward(), angleSum / 4, expected);
        if (normal.lengthSquared() < 1.0E-8F) {
            normal.set(expected);
        } else {
            normal.normalize();
            if (normal.dot(expected) < 0) {
                normal.negate();
            }
        }
        normals[q * 3] = normal.x;
        normals[q * 3 + 1] = normal.y;
        normals[q * 3 + 2] = normal.z;
        for (int i = 0; i < 4; i++) {
            int p = q * 12 + i * 3;
            positions[p] = turned[i].x / ModelPart.Vertex.SCALE_FACTOR;
            positions[p + 1] = turned[i].y / ModelPart.Vertex.SCALE_FACTOR;
            positions[p + 2] = turned[i].z / ModelPart.Vertex.SCALE_FACTOR;
        }
    }

    /**
     * Draws the skirt quads. Built as an empty box (no polygons), so anything that reads cube geometry sees nothing,
     * and {@code compile} is overridden so renderers that patch {@code Cube.compile} (Sodium) never take over.
     */
    private final class SkirtCube extends ModelPart.Cube {
        private final Vector3f scratch = new Vector3f();

        SkirtCube() {
            super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, EnumSet.noneOf(Direction.class));
        }

        @Override
        public void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
            Matrix4f matrix = pose.pose();
            for (int q = 0; q < quads.size(); q++) {
                Vector3f n = pose.transformNormal(normals[q * 3], normals[q * 3 + 1], normals[q * 3 + 2], scratch);
                float nx = n.x();
                float ny = n.y();
                float nz = n.z();
                float[][] uvs = quads.get(q).uvs();
                for (int i = 0; i < 4; i++) {
                    int p = q * 12 + i * 3;
                    Vector3f v = matrix.transformPosition(positions[p], positions[p + 1], positions[p + 2], scratch);
                    buffer.addVertex(v.x(), v.y(), v.z(), color, uvs[i][0], uvs[i][1], packedOverlay, packedLight,
                            nx, ny, nz);
                }
            }
        }
    }

    // ---- Rest-pose construction (once per garment) ----

    private static List<Quad> buildQuads(Spec spec) {
        List<Quad> quads = new ArrayList<>();
        int nF = spec.frontSegments();
        int nS = spec.sideSegments();
        float[][] previousRing = null;
        Band previous = null;
        for (Band band : spec.bands()) {
            float[][] ring = ring(band.hx(), band.hz(), band.pleat(), nF, nS);
            // Ledge from the ring above (previous band, or the fixed start ring) out to this band.
            if (previous != null && (previous.hx() != band.hx() || previous.hz() != band.hz())) {
                addLedge(quads, spec, previousRing, previous.hz(), ring, band);
            } else if (previous == null && spec.startHx() > 0) {
                addLedge(quads, spec, ring(spec.startHx(), spec.startHz(), 0, nF, nS), spec.startHz(), ring, band);
            }
            addBand(quads, spec, ring, band);
            previous = band;
            previousRing = ring;
        }
        return quads;
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

    private static void addBand(List<Quad> quads, Spec spec, float[][] ring, Band band) {
        float w = 2 * band.hx();
        float d = 2 * band.hz();
        float h = band.yBottom() - band.yTop();
        int n = ring.length;
        for (int k = 0; k < n; k++) {
            float[] a = ring[k];
            float[] c = ring[(k + 1) % n];
            int face = (int) a[2];
            float[][] corners = {
                    {a[0], band.yTop(), a[1]}, {c[0], band.yTop(), c[1]},
                    {c[0], band.yBottom(), c[1]}, {a[0], band.yBottom(), a[1]}};
            float[][] uvs = new float[4][];
            for (int i = 0; i < 4; i++) {
                float x = corners[i][0];
                float z = corners[i][2];
                // The shared corner point uses the edge this segment belongs to.
                float u = switch (face) {
                    case 0 -> band.u() + d + Mth.clamp(x + band.hx(), 0, w);
                    case 1 -> band.u() + d + w + Mth.clamp(z + band.hz(), 0, d);
                    case 2 -> band.u() + 2 * d + w + Mth.clamp(band.hx() - x, 0, w);
                    default -> band.u() + Mth.clamp(band.hz() - z, 0, d);
                };
                float v = band.v() + d + (i < 2 ? 0 : h);
                uvs[i] = new float[] {u / spec.textureWidth(), v / spec.textureHeight()};
            }
            float hz = band.hz();
            quads.add(new Quad(corners, uvs, new float[] {hz, hz, hz, hz}, FACE_OUTWARD[face]));
        }
    }

    private static void addLedge(List<Quad> quads, Spec spec, float[][] inner, float innerHz, float[][] outer, Band band) {
        float w = 2 * band.hx();
        float d = 2 * band.hz();
        float y = band.yTop();
        int n = outer.length;
        for (int k = 0; k < n; k++) {
            float[] i0 = inner[k];
            float[] i1 = inner[(k + 1) % n];
            float[] o1 = outer[(k + 1) % n];
            float[] o0 = outer[k];
            float[][] corners = {{i0[0], y, i0[1]}, {i1[0], y, i1[1]}, {o1[0], y, o1[1]}, {o0[0], y, o0[1]}};
            float[][] uvs = new float[4][];
            for (int i = 0; i < 4; i++) {
                float u = band.u() + d + Mth.clamp(corners[i][0] + band.hx(), 0, w);
                float v = band.v() + Mth.clamp(band.hz() - corners[i][2], 0, d);
                uvs[i] = new float[] {u / spec.textureWidth(), v / spec.textureHeight()};
            }
            // The two rings have different depths; blend by each corner's own ring.
            quads.add(new Quad(corners, uvs, new float[] {innerHz, innerHz, band.hz(), band.hz()}, UP));
        }
    }

    // ---- Geometry helpers ----

    /** Back edge (z = +hz) follows {@code back}, front edge (z = -hz) follows {@code front}, blended in between. */
    private static float angleAt(float z, float hz, float front, float back) {
        float n = Mth.clamp(z / hz, -1.0F, 1.0F);
        float t = (1.0F - n) * 0.5F; // 0 at the back, 1 at the front
        return back + (front - back) * t;
    }

    /** Rotates a body-space point about the hip line (x axis through y = HIP_Y, z = 0) into {@code out}. */
    private static void turn(float x, float y, float z, float angle, Vector3f out) {
        float dy = y - HIP_Y;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        out.set(x, HIP_Y + dy * cos - z * sin, dy * sin + z * cos);
    }

    private static void turnDirection(Vector3f dir, float angle, Vector3f out) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        out.set(dir.x, dir.y * cos - dir.z * sin, dir.y * sin + dir.z * cos);
    }
}
