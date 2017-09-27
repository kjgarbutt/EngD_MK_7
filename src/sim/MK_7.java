package sim;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import com.linuxense.javadbf.*;
import java.io.*;

import sim.engine.SimState;
import sim.field.geo.GeomVectorField;
import sim.io.geo.ShapeFileImporter;
import sim.util.Bag;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.GeomPlanarGraphEdge;
import sim.util.geo.MasonGeometry;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.planargraph.Node;

import ec.util.MersenneTwisterFast;

/**
 *
 * "MK_7" is the seventh iteration of my EngD project model. It is adapted from
 * the MASON demo, "Gridlock", made by Sarah Wise, Mark Coletti, and Andrew Crooks.
 * 
 * The model reads a number of GIS shapefiles and displays a road network, two
 * Environment Agency flood maps and a bespoke Open Source Vulnerability Index (OSVI).
 * The model reads in a .CSV and generates a predetermined number of agents with set
 * characteristics. The agents are placed on the road network and are located at
 * a Red Cross office. The model reads a separate .CSV and assigns goal locations to 
 * each agent at random from a predetermined list. The agents are assigned speeds at
 * random.
 * Once the model is started, the agents move from A to B, then they change direction
 * and head back to their start position. The process repeats until the user quits.
 *
 * @author KJGarbutt
 *
 */
public class MK_7 extends SimState	{

	//////////////////////////////////////////////////////////////////////////////
	///////////////////////////// MODEL PARAMETERS ///////////////////////////////
	//////////////////////////////////////////////////////////////////////////////

	private static final long serialVersionUID = -4554882816749973618L;

	///////////////////////////// Containers /////////////////////////////////////
    public GeomVectorField roads = new GeomVectorField();
    public GeomVectorField world = new GeomVectorField();
    public GeomVectorField flood3 = new GeomVectorField();
    public GeomVectorField flood2 = new GeomVectorField();
    //public GeomVectorField HouseholdsFZ = new GeomVectorField();
    //public GeomVectorField Households = new GeomVectorField();
    public GeomVectorField agents = new GeomVectorField();
    //public GeomVectorField ngoagents = new GeomVectorField();

    ///////////////////////////// Network ////////////////////////////////////////
    public GeomPlanarGraph network = new GeomPlanarGraph();
    // Stores road network connections
    public GeomVectorField junctions = new GeomVectorField();
    // Stores nodes for road intersections
    HashMap<Integer, GeomPlanarGraphEdge> idsToEdges =
        new HashMap<Integer, GeomPlanarGraphEdge>();
    public HashMap<GeomPlanarGraphEdge, ArrayList<agents.MainAgent>> edgeTraffic =
        new HashMap<GeomPlanarGraphEdge, ArrayList<agents.MainAgent>>();
    public GeomVectorField mainagents = new GeomVectorField();

    // Model ArrayLists for agents and OSVI Polygons
    ArrayList<agents.MainAgent> agentList = new ArrayList<agents.MainAgent>();
    ArrayList<Polygon> polys = new ArrayList<Polygon>();
    ArrayList<String> csvData = new ArrayList<String>();

    // Here we force the agents to go to or from work at any time
    public boolean goToWork = true;
    public boolean getGoToWork()	{
        return goToWork;
    }

    //////////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN FUNCTIONS //////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////

    /**
     * //////////////////////// Model Constructor ////////////////////////////////
     * Model Constructor
     */
    public MK_7(long seed)	{
        super(seed);
        random = new MersenneTwisterFast(12345);
    }

    /**
     * //////////////////////// OSVI Polygon Setup ///////////////////////////////
     * Polygon Setup
     */
    void setup()
    {
        // copy over the geometries into a list of Polygons
        Bag ps = world.getGeometries();
        polys.addAll(ps);
        }

    /**
     * //////////////////////// Model Initialisation /////////////////////////////
     * Model Initialisation
     */
    @Override
    public void start()	{
        super.start();
        System.out.println("Reading shapefiles...");

		//////////////////////////////////////////////////////////////////////////
        /////////////////////////// READ IN DATA /////////////////////////////////
		//////////////////////////////////////////////////////////////////////////

        /*
        src/
        	data/
            	.shp, .dbf, .csv ...
        sim/
        	MK_7/
        		.java files
         */

        try {
            // read in the roads shapefile to create the transit network
        	URL roadsFile = MK_7.class.getResource
        			("/data/Final_ITN.shp");
            ShapeFileImporter.read(roadsFile, roads);
            System.out.println("	Roads shapefile: " +roadsFile);

            Envelope MBR = roads.getMBR();

            // read in the LSOA shapefile to create the backgrounds
            // URL areasFile = MK_4.class.getResource
            //		("/data/Final_LSOA.shp");
	        //   Bag desiredAttributes = new Bag();
	        //   desiredAttributes.add("RC_RankCol");
            //
	        //   try {
	        //   	ShapeFileImporter.read(areasFile, world, desiredAttributes);
	        //   }
	        //   catch (FileNotFoundException ex){
	        //   	}

            URL wardsFile = MK_7.class.getResource
                    ("/data/Final_LSOA.shp");
            ShapeFileImporter.read(wardsFile, world, Polygon.class);
            System.out.println("	LSOA shapefile: " +wardsFile);

            MBR.expandToInclude(world.getMBR());

            // read in the FZ3 file
            URL flood3File = MK_7.class.getResource
            		("/data/NorfolkFZ3.shp");
            ShapeFileImporter.read(flood3File, flood3);
            System.out.println("	FZ3 shapefile: " +flood3File);

            MBR.expandToInclude(flood3.getMBR());

            // read in the FZ2 file
            URL flood2File = MK_7.class.getResource
            		("/data/NorfolkFZ2.shp");
            ShapeFileImporter.read(flood2File, flood2);
            System.out.println("	FZ2 shapefile: " +flood2File);

            MBR.expandToInclude(flood2.getMBR());

            /*
            // read in the household files
            URL HouseholdsFZFile = MK_2.class.getResource
            		("/data/Buildings_IN_FZ_Snapped_to_ITN.shp");
            ShapeFileImporter.read(HouseholdsFZFile, HouseholdsFZ);
            System.out.println("Households in FZ shapefile: " +HouseholdsFZFile);

            MBR.expandToInclude(HouseholdsFZ.getMBR());

            // read in the FZ2 file
            URL HouseholdsFile = MK_2.class.getResource
            		("/data/Buildings_NOT_in_FZ_Snapped_to_ITN.shp");
            ShapeFileImporter.read(HouseholdsFile, Households);
            System.out.println("Households not in FZ shapefile: " +HouseholdsFile);
            System.out.println();

            MBR.expandToInclude(Households.getMBR());
            */

            createNetwork();
            setup();

            //////////////////////////////////////////////////////////////////////
            /////////////////////////// CLEANUP //////////////////////////////////
            //////////////////////////////////////////////////////////////////////

            // clear any existing agents from previous runs
            agents.clear();
            //ngoagents.clear();

            //////////////////////////////////////////////////////////////////////
            /////////////////////////// AGENTS ///////////////////////////////////
            //////////////////////////////////////////////////////////////////////

            // initialize agents using the following source .CSV files
            agentGoals("/data/AgentGoals.csv");
            populateAgent("/data/NorfolkITNAGENT.csv");

            System.out.println();
            System.out.println("Starting simulation...");

            // standardize the MBRs so that the visualization lines up
            // and everyone knows what the standard MBR is
            roads.setMBR(MBR);
            world.setMBR(MBR);
            flood3.setMBR(MBR);
            flood2.setMBR(MBR);
            //HouseholdsFZ.setMBR(MBR);
            //Households.setMBR(MBR);
            agents.setMBR(MBR);
            //ngoagents.setMBR(MBR);

            // Ensure that the spatial index is updated after all the agents move
            schedule.scheduleRepeating( agents.scheduleSpatialIndexUpdater(),
            		Integer.MAX_VALUE, 1.0);
            //schedule.scheduleRepeating( ngoagents.scheduleSpatialIndexUpdater(),
            //		Integer.MAX_VALUE, 1.0);


            /** Steppable that flips Agent paths once everyone reaches
             * their destinations

            Steppable flipper = new Steppable() {
                @Override
                public void step(SimState state)
                {

                    MK_7 gstate = (MK_7) state;

                    // pass to check if anyone has not yet reached work
                    //for (MainAgent a : gstate.agentList)
                    //{
                        ///if (!a.reachedDestination)
                        //{
                        //    return; // someone is still moving: let him do so
                      //  }
                    //}
                    // send everyone back in the opposite direction now
                    //boolean toWork = gstate.goToWork;
                   // gstate.goToWork = !toWork;

                    // otherwise everyone has reached their latest destination:
                    // turn them back
                    for (MainAgent a : gstate.agentList)
                        if (a.reachedDestination) {
                            a.flipPath();
                        }
                }
            };
            schedule.scheduleRepeating(flipper, 10);
             */

        } catch (FileNotFoundException e)
        {
            System.out.println("Error: missing required data file");
        } catch (IOException e) {
			e.printStackTrace();
		}
    }

	/**
	 * //////////////////////// Model Finish & Cleanup ///////////////////////////
	 * Finish the simulation and clean up
	 */
    public void finish()	{
    	super.finish();
    	System.out.println();
    	System.out.println("Simulation ended by user.");
        /*
    	System.out.println("Attempting to export agent data...");
        try	{
        	ShapeFileExporter.write("agents", agents);
        } catch (Exception e)	{
        	System.out.println("Export failed.");
        	e.printStackTrace();
        }
        */
    }

    /**
     * //////////////////////// Create Road Network //////////////////////////////
     * Create the road network the agents will traverse
     */
    private void createNetwork()	{
    	System.out.println("Creating road network..." +roads);
    	System.out.println();
    	network.createFromGeomField(roads);

        for (Object o : network.getEdges())	{
            GeomPlanarGraphEdge e = (GeomPlanarGraphEdge) o;

            idsToEdges.put(e.getIntegerAttribute("ROAD_ID").intValue(), e);

            e.setData(new ArrayList<agents.MainAgent>());
        }

        addIntersectionNodes(network.nodeIterator(), junctions);
    }
    
    
	/**
	* ///////////////////////// Setup agentGoals /////////////////////////////////
	* Read in the agent goals CSV
	* @param agentfilename
	* @return
	*
	*/
	public ArrayList<String> agentGoals(String agentfilename) throws IOException{
		String csvGoal = null;
		BufferedReader agentGoalsBuffer = null;

		String agentFilePath = MK_7.class.getResource(agentfilename).getPath();
		FileInputStream agentfstream = new FileInputStream(agentFilePath);
		System.out.println("Reading Agent's Goals CSV file: " +agentFilePath);

		try {
			agentGoalsBuffer = new BufferedReader
					(new InputStreamReader(agentfstream));
			agentGoalsBuffer.readLine();
			while ((csvGoal = agentGoalsBuffer.readLine()) != null) {
				String[] splitted = csvGoal.split(",");

				ArrayList<String> agentGoalsResult = 
						new ArrayList<String>(splitted.length);
				for (String data : splitted)
					agentGoalsResult.add(data);
				csvData.addAll(agentGoalsResult);
			}
			System.out.println("Full csvData Array: " +csvData);

		} finally {
			if (agentGoalsBuffer != null)
				agentGoalsBuffer.close();
		}
		return csvData;
	}

    /**
     * //////////////////////// Setup mainAgent //////////////////////////////////
     * Read in the population files and create appropriate populations
     * @param filename
     */
    public void populateAgent(String filename)	{
        try	{
            String filePath = MK_7.class.getResource(filename).getPath();
            FileInputStream fstream = new FileInputStream(filePath);
            System.out.println();
            System.out.println("Populating model with Agents: " +filePath);

            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{
                String[] bits = s.split(",");

                int pop = Integer.parseInt(bits[2]);

                //moveRate = (int)(Math.random()*70) + 1;
                //System. out.println("MoveRate = " + moveRate );
				//int mainAgentSpeed = MainAgent.MoveRate;
                //System.out.println("Main Agent speed = " +mainAgentSpeed);

                String homeTract = bits[3];
                String ROAD_ID = bits[3];

                Random randomiser = new Random();
                String random = csvData.get(new Random().nextInt(csvData.size()));
                String goalTract = random;
                System.out.println();
                System.out.println("Agent goalTract: " +goalTract);

                GeomPlanarGraphEdge startingEdge = idsToEdges.get(
                        (int) Double.parseDouble(ROAD_ID));
                GeomPlanarGraphEdge goalEdge = idsToEdges.get(
                        (int) Double.parseDouble(goalTract));
                		//reads the .CSV column
                		//goals[ random.nextInt(goals.length)]);
                		// uses the hardcoded 'goals' from above

                for (int i = 0; i < pop; i++)	{
                	//pop; i++)	{ 	// NO IDEA IF THIS MAKES A DIFFERENCE!?!
                    agents.MainAgent a = new agents.MainAgent
                    		(this, homeTract, startingEdge, goalEdge);
                    
                    boolean successfulStart = a.start(this);
                    //System.out.println("Starting...");

                    if (!successfulStart)	{
                    	System.out.println("Main agents added successfully!!");
                    	continue; // DON'T ADD IT if it's bad
                    }

                    //MasonGeometry newGeometry = new MasonGeometry(a.getGeometry());
                    MasonGeometry newGeometry = a.getGeometry();
                    newGeometry.isMovable = true;
                    agents.addGeometry(newGeometry);
                    agentList.add(a);
                    schedule.scheduleRepeating(a);
                }
            }

            d.close();

        } catch (Exception e) {
		    	System.out.println("ERROR: issue with population file: ");
				e.printStackTrace();
		}
    }

    /**
     * //////////////////////// Network Intersections ////////////////////////////
     * adds nodes corresponding to road intersections to GeomVectorField
     *
     * @param nodeIterator Points to first node
     * @param intersections GeomVectorField containing intersection geometry
     *
     * Nodes will belong to a planar graph populated from LineString network.
     */
    private void addIntersectionNodes(Iterator<?> nodeIterator,
                                      GeomVectorField intersections)	{
        GeometryFactory fact = new GeometryFactory();
        Coordinate coord = null;
        Point point = null;
        @SuppressWarnings("unused")
		int counter = 0;

        while (nodeIterator.hasNext())	{
            Node node = (Node) nodeIterator.next();
            coord = node.getCoordinate();
            point = fact.createPoint(coord);

            junctions.addGeometry(new MasonGeometry(point));
            counter++;
        }
    }

    /**
     * //////////////////////// Main Function ////////////////////////////////////
     * Main function allows simulation to be run in stand-alone, non-GUI mode
     */
    public static void main(String[] args)	{
        doLoop(MK_7.class, args);
        System.exit(0);
    }
}
