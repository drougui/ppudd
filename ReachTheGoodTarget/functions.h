// parse parameters, save current problem
void parse_parameters(unsigned &grid_size, unsigned &nb_walls, double &discount_factor, unsigned &DM, unsigned &CM, bool &well_loaded);
void save_grid(bool** walls_around_spot, bool* walls_locations, unsigned grid_side_size);

// create rddl file
void generate_header_rddl_file(std::ofstream & rddl_file);
void generate_instance_rddl_file(std::ofstream & rddl_file);
void generate_reward_rddl_file(std::ofstream & rddl_file, unsigned grid_side_size, unsigned spots_number);
void generate_variables_rddl_file(std::ofstream & rddl_file, unsigned spots_number);
void generate_CPFS_Observations_rddl_file(std::ofstream & rddl_file, unsigned grid_side_size, unsigned spots_number, double CM, double DM);
void generate_CPFS_States_rddl_file(std::ofstream & rddl_file, unsigned spots_number, std::string* preconditions);

// transitions computation
bool** walls_loc2description(bool* walls_locations, unsigned grid_side_size);
bool*** compute_state_transition(unsigned grid_side_size, unsigned spots_number, bool** walls_around);
std::string* transitions2preconds(bool*** transitions, unsigned spots_number);

// utils
bool* number2binary(unsigned number, unsigned nbBooleans);
void plot_transitions(bool*** transitions,unsigned xmax, unsigned ymax, unsigned zmax);
