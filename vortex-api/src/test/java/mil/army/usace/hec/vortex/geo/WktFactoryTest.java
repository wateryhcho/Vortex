package mil.army.usace.hec.vortex.geo;

import mil.army.usace.hec.vortex.GdalRegister;
import mil.army.usace.hec.vortex.VortexData;
import mil.army.usace.hec.vortex.VortexGrid;
import mil.army.usace.hec.vortex.io.DataReader;
import mil.army.usace.hec.vortex.io.DataWriter;
import org.gdal.osr.SpatialReference;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WktFactoryTest {
    static {
        GdalRegister.getInstance();
    }

    @Test
    void MrmsPrecipWktPassesRegression() {
        Path inFile = new File(getClass().getResource(
                "/MRMS_GaugeCorr_QPE_01H_00.00_20170102-120000.grib2").getFile()).toPath();

        String variableName = "GaugeCorrQPE01H_altitude_above_msl";

        DataReader reader = DataReader.builder().path(inFile).variable(variableName).build();
        List<VortexGrid> data = reader.getDTOs().stream().map(grid -> (VortexGrid) grid).collect(Collectors.toList());
        VortexGrid grid = data.get(0);
        String wkt = grid.wkt();

        SpatialReference srs = new SpatialReference(wkt);
        assertEquals(6371229, srs.GetSemiMajor(), 1E-3);
        assertEquals(0, srs.GetInvFlattening(), 1E-3);
        assertEquals("degree", srs.GetAngularUnitsName().toLowerCase());

        List<VortexData> grids = new ArrayList<>();
        grids.add(grid);

        DataWriter.builder()
                .data(grids)
                .destination(Paths.get("C:/Temp/test.dss"))
                .build()
                .write();
    }

    @Test
    void RtmaTemperatureWktPassesRegression() {
        Path inFile = new File(getClass().getResource(
                "/201701021200_TMPK.grib2").getFile()).toPath();

        String variableName = "Temperature_height_above_ground";

        DataReader reader = DataReader.builder().path(inFile).variable(variableName).build();
        List<VortexGrid> data = reader.getDTOs().stream().map(grid -> (VortexGrid) grid).collect(Collectors.toList());
        VortexGrid grid = data.get(0);
        String wkt = grid.wkt();

        SpatialReference srs = new SpatialReference(wkt);
        assertEquals(6371200, srs.GetSemiMajor(), 1E-3);
        assertEquals(0.0, srs.GetInvFlattening(), 1E-3);
        assertEquals("Lambert_Conformal_Conic_2SP", srs.GetAttrValue("PROJECTION"));
        assertEquals(25, srs.GetProjParm("standard_parallel_1"), 1E-3);
        assertEquals(25, srs.GetProjParm("standard_parallel_2"), 1E-3);
        assertEquals(25, srs.GetProjParm("latitude_of_origin"), 1E-3);
        assertEquals(265, srs.GetProjParm("central_meridian"), 1E-3);
        assertEquals(0, srs.GetProjParm("false_easting"), 1E-3);
        assertEquals(0, srs.GetProjParm("false_northing"), 1E-3);
        assertEquals("degree", srs.GetAngularUnitsName().toLowerCase());
        assertEquals("meter", srs.GetLinearUnitsName().toLowerCase());

        List<VortexData> grids = new ArrayList<>();
        grids.add(grid);

        DataWriter.builder()
                .data(grids)
                .destination(Paths.get("C:/Temp/test.dss"))
                .build()
                .write();
    }
}