#include <iostream>
#include <fstream>
#include <math.h>
#include <cstdlib>
#include "functions.h"
//#include <time.h>
int main()
{
  	// parameters
  	unsigned grid_size;
  	unsigned nb_walls;
	double discount_factor;
	unsigned DM;
	unsigned CM;
	
  	// load parameters -- parse parameters.txt
	bool well_loaded = true;
	std::cout << std::endl;
	parse_parameters(grid_size, nb_walls, discount_factor, DM, CM, well_loaded);

	// visualize parameters
	if (well_loaded)
	{
		std::cout << "\nall needed parameters have been loaded:\n grid_size = " << grid_size << ";\n" << " walls_number = " << nb_walls << ";\n" << " discount = " << discount_factor << ";\n" << " DM = " << DM << ";\n" << " CM = " << CM << ".\n" << std::endl;
	}
	else
	{
		std::cerr << "\nWARNING! problem loading parameters:\n grid_size = " << grid_size << ";\n" << " walls_number = " << nb_walls << ";\n" << " discount = " << discount_factor << ";\n" << " DM = " << DM << ";\n" << " CM = " << CM << "." << std::endl;
	}

	unsigned nb_spots = grid_size*grid_size; // number of possible spots for the agent


	// grid definition
	std::cout << "grid definition\n" << std::endl; 
	srand(time(NULL)); 
	// random locations of walls: true = "wall"; 
	//                            false = "no wall".
	bool* walls_loc = new bool[nb_spots]; 
	for (unsigned i=0;i<nb_spots;i++)
		walls_loc[i] = false;
	for (unsigned i=0;i<nb_walls;i++)
	{
		unsigned loc = rand()%nb_spots;
		// no wall allowed at the targets locations, or at initial robot location
		while (walls_loc[loc]==true || loc==0 || loc==grid_size-1 || loc==nb_spots-grid_size) 
		{
			loc = rand()%nb_spots;
		}
		walls_loc[loc]=true;
	}

	// for each location "s", description of the wall around 
	bool** walls_around = walls_loc2description(walls_loc,grid_size);
	
	// save the grid in the file grid.txt
	std::cout << "save the grid in grid.txt:" << std::endl;	
	save_grid(walls_around,walls_loc,grid_size);

	// generate the rddl file
	std::ofstream pomdp_rddl_file("input/RTGT_pomdp.rddl", std::ios::out | std::ios::trunc );
	pomdp_rddl_file.precision(10);

	generate_header_rddl_file(pomdp_rddl_file);

	generate_variables_rddl_file(pomdp_rddl_file, nb_spots);

	generate_CPFS_Observations_rddl_file(pomdp_rddl_file, grid_size, nb_spots, CM, DM);

	bool*** robot_location_transitions = compute_state_transition(grid_size, nb_spots, walls_around);

	std::string* preconditions = transitions2preconds(robot_location_transitions, nb_spots);

	generate_CPFS_States_rddl_file(pomdp_rddl_file, nb_spots, preconditions);

	generate_reward_rddl_file(pomdp_rddl_file, grid_size, nb_spots);

	generate_instance_rddl_file(pomdp_rddl_file);

	pomdp_rddl_file.close();

	// free memory
	delete [] walls_loc;
	for (unsigned i=0;i<nb_spots;i++)
		delete [] walls_around[i];
	delete [] walls_around;
	for (unsigned i=0;i<nb_spots;i++)
	{	
		for (unsigned j=0;j<nb_spots;j++)
		{
			delete [] robot_location_transitions[i][j];
		}
		delete [] robot_location_transitions[i];
	}
	delete [] robot_location_transitions;
	delete [] preconditions;

	return 0; 
}
