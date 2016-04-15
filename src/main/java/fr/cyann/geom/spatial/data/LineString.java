package fr.cyann.geom.spatial.data; /**
 * Copyright (C) 18/12/15 Yann Caron aka cyann
 * <p>
 * Cette œuvre est mise à disposition sous licence Attribution -
 * Pas d’Utilisation Commerciale - Partage dans les Mêmes Conditions 3.0 France.
 * Pour voir une copie de cette licence, visitez http://creativecommons.org/licenses/by-nc-sa/3.0/fr/
 * ou écrivez à Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
 **/

import fr.cyann.geom.spatial.data.coord.XY;
import fr.cyann.geom.spatial.data.coord.XYM;
import fr.cyann.geom.spatial.data.coord.XYZ;
import fr.cyann.geom.spatial.data.coord.XYZM;
import fr.cyann.geom.spatial.data.parsing.BinaryUtil;
import fr.cyann.geom.spatial.data.parsing.GeometryType;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Stack;

/**
 * The ch.skyguide.geos.loader.geom.LineString definition.
 */
public class LineString<C extends XY> extends Geometry {

	private final CoordList<C> coordinate;

	LineString(Class<C> type, CoordList<C> coordinate) {
		super(type);
		this.coordinate = coordinate;
	}

	protected LineString(Class<C> type, boolean closed) {
		this(type, new CoordList<C>(closed));
	}

	public LineString(Class<C> type) {
		this(type, new CoordList(false));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		LineString<?> that = (LineString<?>) o;

		return !(coordinate != null ? !coordinate.equals(that.coordinate) : that.coordinate != null);

	}

	@Override
	public int hashCode() {
		return coordinate != null ? coordinate.hashCode() : 0;
	}

	public CoordList<C> getCoordinate() {
		return coordinate;
	}

	public LineString<C> addToCoordinate(C coordinate) {
		this.coordinate.add(coordinate);
		return this;
	}

	@Override
	public void marshall(StringBuilder stringBuilder) throws BadGeometryException {
		stringBuilder.append("LINESTRING");
		appendType(stringBuilder);
		stringBuilder.append(' ');
		coordinate.marshall(stringBuilder);
	}

	public static <C extends XY> LineString<C> unMarshall(Class<C> type, String string) {
		if (string == null) return null;
		return unMarshall(type, new StringBuilder(string));
	}

	public static <C extends XY> LineString<C> unMarshall(Class<C> type, StringBuilder stringBuilder) {

		// LINESTRING
		Parse.removeBlanks(stringBuilder);
		Class<? extends XY> parsedType = getCoordType(stringBuilder, "LINESTRING");
		if (type == null || !type.equals(parsedType)) return null;

		CoordList<C> list = CoordList.unMarshall(type, stringBuilder, false);
		if (list == null) return null;

		return new LineString<C>(type, list);
	}

	public static LineString<? extends XY> unMarshall(byte[] bytes) {
		ByteBuffer buffer = BinaryUtil.toByteBufferEndianness(bytes);
		int geometryType = buffer.getInt();
		if (geometryType == GeometryType.LINESTRING.getCode()) return unMarshall(XY.class, buffer);
		if (geometryType == GeometryType.LINESTRINGZ.getCode()) return unMarshall(XYZ.class, buffer);
		if (geometryType == GeometryType.LINESTRINGM.getCode()) return unMarshall(XYM.class, buffer);
		if (geometryType == GeometryType.LINESTRINGZM.getCode()) return unMarshall(XYZM.class, buffer);
		return null;
	}

	public static <C extends XY> LineString<C> unMarshall(Class<C> type, ByteBuffer buffer) {
		return new LineString<C>(type, CoordList.unMarshall(type, buffer, false));
	}

	public static final double EARTH_RADIUS_KM = 6378137d;
	public static final double EARTH_EQUATORIAL_PERIMETER_KM = 2.0d * Math.PI * EARTH_RADIUS_KM;
	public static final double EARTH_EQUATORIAL_PERIMETER_DEG = EARTH_EQUATORIAL_PERIMETER_KM / 360;

	public static LineString<XYZM> simplify(LineString<XYZM> lineString, double delta) {
		class Bounds {
			final int begin;
			final int end;
			final int endOfCreated;

			public Bounds(int begin, int end, int endOfCreated) {
				this.begin = begin;
				this.end = end;
				this.endOfCreated = endOfCreated;
			}
		}

		CoordList<XYZM> resCoord = new CoordList<>(lineString.getCoordinate().isClosed());
		List<XYZM> coords = lineString.getCoordinate().getCoords();

		Stack<Bounds> bounds = new Stack<Bounds>();
		bounds.add(new Bounds(0, coords.size(), 1));
		resCoord.add(coords.get(0));
		resCoord.add(coords.get(coords.size() - 1));

		while (!bounds.isEmpty()) {
			Bounds bound = bounds.pop();
			XYZM a = coords.get(bound.begin);
			XYZM b = coords.get(bound.end - 1);
			XYZM ra = new XYZM(a.getX(), a.getY(), a.getZ() / EARTH_EQUATORIAL_PERIMETER_DEG, a.getM());
			XYZM rb = new XYZM(b.getX(), b.getY(), b.getZ() / EARTH_EQUATORIAL_PERIMETER_DEG, b.getM());

			double maxDist = 0;
			int pivot = -1;
			for (int i = bound.begin; i < bound.end; i++) {
				XYZM c = coords.get(i);
				XYZM rc = new XYZM(c.getX(), c.getY(), c.getZ() / EARTH_EQUATORIAL_PERIMETER_DEG, c.getM());

				//double dist = c.distanceToSegment(a, b);
				double dist = rc.distanceToSegment(ra, rb);
				if (dist > delta && dist > maxDist) {
					maxDist = dist;
					pivot = i;
				}
			}

			if (pivot != -1) {
				XYZM p = coords.get(pivot);
				resCoord.add(bound.endOfCreated, p);
				bounds.add(new Bounds(bound.begin, pivot, bound.endOfCreated));
				bounds.add(new Bounds(pivot, bound.end, bound.endOfCreated + 1));
			}
		}

		return new LineString<XYZM>(lineString.getCoordinateType(), resCoord);
	}

	public static XYZM searchEpoch(LineString<XYZM> lineString, double search) {
		List<XYZM> coords = lineString.getCoordinate().getCoords();

		// contains point
		XYZM first = coords.get(0);
		XYZM last = coords.get(coords.size() - 1);

		if (first.getM() <= search && last.getM() >= search) {
			return binarySearch(lineString.getCoordinate().getCoords(), search);
		}
		return null;
	}

	private static XYZM binarySearch(List<XYZM> coords, double search) {

		int begin = 0;
		int end = coords.size() - 1;

		while (begin <= end) {
			int delta = end - begin;
			int mid = delta / 2 + begin;
			XYZM coord = coords.get(mid);

			if (coord.getM() == search || delta == 0) {
				return coord;
			} else if (delta == 1) {
				return calculateAlong(coords.get(begin), coords.get(end), search);
			} else if (search < coord.getM()) {
				end = mid;
			} else {
				begin = mid;
			}

		}

		return null;

	}

	private static XYZM calculateAlong(XYZM v1, XYZM v2, double time) {
		double proportion = (time - v1.getM()) / (v2.getM() - v1.getM());
		if (Double.isInfinite(proportion)) {
			proportion = 0.0;
		}

		double x = v1.getX() + proportion * (v2.getX() - v1.getX());
		double y = v1.getY() + proportion * (v2.getY() - v1.getY());
		double z = v1.getZ() + proportion * (v2.getZ() - v1.getZ());

		return new XYZM(x, y, z, time);
	}

}
