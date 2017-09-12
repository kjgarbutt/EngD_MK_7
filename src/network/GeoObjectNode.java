package network;

import com.vividsolutions.jts.geom.Geometry;

public class GeoObjectNode extends GeoNode {

	Object object = null;
	
	public GeoObjectNode(Geometry g) {
		super(g);
	}

	public GeoObjectNode(Geometry g, Object o) {
		super(g);
		this.object = o;
	}

	public Object getObject(){ return object; }
}