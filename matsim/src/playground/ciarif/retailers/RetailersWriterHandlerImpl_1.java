package playground.ciarif.retailers;

import java.io.BufferedWriter;
import java.io.IOException;

public class RetailersWriterHandlerImpl_1 implements RetailersWriterHandler {
	// interface implementation
	//////////////////////////////////////////////////////////////////////
	// <retailers ... > ... </retailers>
	//////////////////////////////////////////////////////////////////////
	public void startRetailers(final Retailers retailers, final BufferedWriter out) throws IOException {
		out.write("<retailers ");
		out.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
		out.write("xsi:noNamespaceSchemaLocation=\"http://matsim.org/files/dtd/retailers_v1.xsd\"\n");

		if (retailers.getName() != null) {
			out.write(" name=\"" + retailers.getName() + "\"");
		}
		if (retailers.getDescription() != null) {
			out.write(" desc=\"" + retailers.getDescription() + "\"");
		}
		out.write(" year=\"" + retailers.getYear() + "\" ");
		if (retailers.getLayer() != null) {
			out.write(" layer=\"" + retailers.getLayer() + "\" \n");
		}
		out.write(" > \n");
	}
	public void endRetailers(final BufferedWriter out) throws IOException {
		out.write("</retailers>\n");
	}
	//////////////////////////////////////////////////////////////////////
	// <retailer ... > ... </retailer>
	//////////////////////////////////////////////////////////////////////
	public void startRetailer(final Retailer retailer, final BufferedWriter out) throws IOException {
		out.write("\t<retailer");
		out.write(" loc_id=\"" + retailer.getLocId() + "\"");
		if (retailer.getCoord() != null) {
			out.write(" x=\"" + retailer.getCoord().getX() + "\"");
			out.write(" y=\"" + retailer.getCoord().getY() + "\"");
		}
		out.write(">\n");
	}
	public void endRetailer(final BufferedWriter out) throws IOException {
		out.write("\t</retailer>\n\n");
	}

	//////////////////////////////////////////////////////////////////////
	// <facility ... />
	//////////////////////////////////////////////////////////////////////
	public void startFacility(final Facility facility, final BufferedWriter out) throws IOException {
		out.write("\t\t<facility");
		out.write(" h=\"" + facility.getCapacity() + "\"");
		out.write(" val=\"" + facility.getMin_cust_sqm() + "\"");
		out.write(" />\n");
	}
	public void endFacility(final BufferedWriter out) throws IOException {
	}
	//////////////////////////////////////////////////////////////////////
	// <!-- ============ ... ========== -->
	//////////////////////////////////////////////////////////////////////
	public void writeSeparator(final BufferedWriter out) throws IOException {
		out.write("<!-- =================================================" +
							"===================== -->\n\n");
	}
}
