#include <iostream>
#include <fstream>
#include <math.h>
#include <string>
#include <sstream>

void plot_transitions(bool*** transitions,unsigned xmax, unsigned ymax, unsigned zmax)
{
	
	for (unsigned z=0;z<zmax;z++)
	{
		std::cout << "\nz: " << z << std::endl;
		for (unsigned x=0; x<xmax; x++)
		{
			for (unsigned y=0;y<ymax;y++)
			{
				if (transitions[x][y][z])
				{
					std::cout << "1 ";
				}
				else
				{
					std::cout << "0 ";
				}
			}
			std::cout << std::endl;
		}
	}
}

bool* number2binary(unsigned number, unsigned nbBooleans)
{
	bool* binaryNumber = new bool[nbBooleans];
	for (unsigned boolIndex=0;boolIndex<nbBooleans;boolIndex++)
		binaryNumber[boolIndex] = (((number & ( 1 << boolIndex )) >> boolIndex)==1);
	return binaryNumber;
}


/*******************************************************************************
	Write the last parts of the RDDL file describing the POMDP.
********************************************************************************/
// INSTANCE (e.g. initial state)
void generate_instance_rddl_file(std::ofstream & rddl_file)
{
	rddl_file << "\ninstance RTGT_inst_pomdp {" << std::endl;
	rddl_file << "	domain = RTGT_pomdp;" << std::endl;
	//*** OU PAS SI PAS VRAI (param)!
	rddl_file << "	init-state {" << std::endl;
	rddl_file << "		targetOneIsA;" << std::endl; 
	rddl_file << "	};" << std::endl;
	//***	
	rddl_file << "	max-nondef-actions = 1;" << std::endl;
	rddl_file << "	horizon = 40;" << std::endl;
	rddl_file << "	discount = 1.0;" << std::endl;
	rddl_file << "}" << std::endl;
}

// REWARDS
void generate_reward_rddl_file(std::ofstream & rddl_file, unsigned grid_side_size, unsigned spots_number)
{
	unsigned T1_location = spots_number - grid_side_size;
	unsigned T2_location = grid_side_size-1;

	unsigned nbBooleans = ceil(log2(spots_number));
	bool* T1_loc_binary = number2binary(T1_location, nbBooleans);
	bool* T2_loc_binary = number2binary(T2_location, nbBooleans);
		
	rddl_file << "\n	reward = [ 100.0*( targetOneIsA ^ (~move-north)^(~move-east)^(~move-south)^(~move-west)";
	for (unsigned v=0;v<nbBooleans;v++)
	{
		rddl_file << " ^ ";
		if (!T1_loc_binary[v])		
			rddl_file << "~";
		rddl_file << "robot-loc-state-";
		rddl_file << v;
	}
	rddl_file << ")" << std::endl;
	rddl_file << "		+ (-100.0)*( (~targetOneIsA) ^ (~move-north)^(~move-east)^(~move-south)^(~move-west)";
	for (unsigned v=0;v<nbBooleans;v++)
	{
		rddl_file << " ^ ";
		if (!T1_loc_binary[v])		
			rddl_file << "~";
		rddl_file << "robot-loc-state-";
		rddl_file << v;
	}
	rddl_file << ")" << std::endl;

	rddl_file << "		+ (-100.0)*( targetOneIsA ^ (~move-north)^(~move-east)^(~move-south)^(~move-west)";
	for (unsigned v=0;v<nbBooleans;v++)
	{
		rddl_file << " ^ ";
		if (!T2_loc_binary[v])		
			rddl_file << "~";
		rddl_file << "robot-loc-state-";
		rddl_file << v;
	}
	rddl_file << ")" << std::endl;

	rddl_file << "		+ 100.0*( (~targetOneIsA) ^ (~move-north)^(~move-east)^(~move-south)^(~move-west)";
	for (unsigned v=0;v<nbBooleans;v++)
	{
		rddl_file << " ^ ";
		if (!T2_loc_binary[v])		
			rddl_file << "~";
		rddl_file << "robot-loc-state-";
		rddl_file << v;
	}
	rddl_file << ")];\n}" << std::endl;

	delete [] T1_loc_binary;
	delete [] T2_loc_binary;
}


/*******************************************************************************
	Write the involved variables in the RDDL file defining the POMDP.
********************************************************************************/
void generate_variables_rddl_file(std::ofstream & rddl_file, unsigned spots_number)
{
	rddl_file << "	pvariables {" << std::endl;
	unsigned nbBooleans = ceil(log2(spots_number));
	rddl_file << "\n		//States" << std::endl;
	for (unsigned v=0;v<nbBooleans;v++)
		rddl_file << "		robot-loc-state-" << v << " : {state-fluent, bool, default = false};" << std::endl;
	rddl_file << "		targetOneIsA : {state-fluent, bool, default = false};" << std::endl;
	rddl_file << "\n		//Observations" << std::endl;
	for (unsigned v=0;v<nbBooleans;v++)
		rddl_file << "		robot-loc-obs-" << v << " : {observ-fluent, bool};" << std::endl;
	rddl_file << "		obs-target-one : {observ-fluent, bool};"  << std::endl;
	rddl_file << "		obs-target-two : {observ-fluent, bool};" << std::endl;
	rddl_file << "\n		// Actions" << std::endl;
	rddl_file << "		move-north : {action-fluent, bool, default = false};" << std::endl;
	rddl_file << "		move-south : {action-fluent, bool, default = false};" << std::endl;
	rddl_file << "		move-east  : {action-fluent, bool, default = false};" << std::endl;
	rddl_file << "		move-west  : {action-fluent, bool, default = false};" << std::endl;
	rddl_file << "\n	};" << std::endl;
}


/*******************************************************************************
	Compute the transitions preconditions 
	from the transition function
********************************************************************************/
std::string* transitions2preconds(bool*** transitions, unsigned spots_number)
{
	unsigned nbBooleans = ceil(log2(spots_number));
	std::string* preconditions = new std::string[nbBooleans];
	
	std::string* action_names = new std::string[5];
	action_names[0] = "(~move-north)^(~move-east)^(~move-south)^(~move-west)";
	action_names[1] = "move-north";
	action_names[2] = "move-east";
	action_names[3] = "move-south";
	action_names[4] = "move-west";

	for (unsigned v=0;v<nbBooleans;v++)
	{
		unsigned * actions_preconds = new unsigned[5*spots_number];
		unsigned * states_preconds = new unsigned[5*spots_number];
		unsigned nb_preconds = 0;
		// store actions and states preconditions
		for (unsigned sp=0; sp<spots_number; sp++)
		{
			bool trueVar = (sp & ( 1 << v )) >> v;
			if (trueVar)
			{
				for (unsigned s=0; s<spots_number; s++)
				{
					for (unsigned a=0; a<5; a++)
					{
						if (transitions[s][sp][a])
						{
							actions_preconds[nb_preconds]=a;
							states_preconds[nb_preconds]=s;
							nb_preconds++;
//							std::cout << sp << " <- " << s << " " << action_names[a] << std::endl;
						}
					}
				}		
			}		
		}

		// fill a string preconditions[v] -- for each boolean state variable robot-location-state' ('v' here) -- 
		// with the preconditions

		// first precondition
		std::string current_preconditions = " (" + action_names[actions_preconds[0]];
		bool* state_binary = number2binary(states_preconds[0], nbBooleans);
		for (unsigned b=0;b<nbBooleans-1;b++)
		{
			current_preconditions += " ^ ";
			if (!state_binary[b])
				current_preconditions += "~";
			current_preconditions += "robot-loc-state-";
			std::stringstream ssb;
			ssb << b;
			std::string strb = ssb.str();
			current_preconditions += strb;
		}
		current_preconditions += " ^ ";
		if (!state_binary[nbBooleans-1])
			current_preconditions += "~";
		current_preconditions += "robot-loc-state-";
		std::stringstream sslastb;
		sslastb << nbBooleans-1;
		std::string strlastb = sslastb.str();
		current_preconditions += strlastb;
		current_preconditions += ")";
		
		// loop over next preconditions
		for (unsigned p=1;p<nb_preconds;p++)
		{
			current_preconditions += "\n					 | (" + action_names[actions_preconds[p]]; 
			bool* state_binary = number2binary(states_preconds[p], nbBooleans);
			for (unsigned b=0;b<nbBooleans-1;b++)
			{
				//std::cout << state_binary[b] << " ";
				current_preconditions += " ^ ";
				if (!state_binary[b])
					current_preconditions += "~";
				current_preconditions += "robot-loc-state-";
				std::stringstream ssb;
				ssb << b;
				std::string strb = ssb.str();
				current_preconditions += strb;
			}
//			std::cout << std::endl;
			current_preconditions += " ^ ";
			if (!state_binary[nbBooleans-1])
				current_preconditions += "~";
			current_preconditions += "robot-loc-state-";
			std::stringstream sslastb;
			sslastb << nbBooleans-1;
			std::string strlastb = sslastb.str();
			current_preconditions += strlastb;
			current_preconditions += ")";
		}
		delete [] actions_preconds;
		delete [] states_preconds;
		preconditions[v] = current_preconditions;
	}
	delete [] action_names;
	return preconditions;
} 

/*******************************************************************************
	Write the state transitions 
	in the RDDL file describing the POMDP.
********************************************************************************/
void generate_CPFS_States_rddl_file(std::ofstream & rddl_file, unsigned spots_number, std::string* preconditions)
{
	unsigned nbBooleans = ceil(log2(spots_number));
	rddl_file << "		// States" << std::endl;
	for (unsigned v=0;v<nbBooleans;v++)
	{
		rddl_file << "\n		robot-loc-state-" << v << "' = if (";
		rddl_file << preconditions[v];
		rddl_file << " )" << std::endl;
		rddl_file << "				then\n				KronDelta(true)" << std::endl;
		rddl_file << "			else\n				KronDelta(false);" << std::endl;			
	}
	rddl_file << "\n		targetOneIsA' = KronDelta(targetOneIsA);" << std::endl;
	rddl_file << "\n	};" << std::endl;
}

/*******************************************************************************
	Compute the transitions of the robot locations,
	and store it in the tensor transitions[spots_number][spots_number][5]
	where 5 is the number of actions.
********************************************************************************/
bool*** compute_state_transition(unsigned grid_side_size, unsigned spots_number, bool** walls_around)
{

	bool*** transitions = new bool**[spots_number];
	for (unsigned i=0;i<spots_number;i++)
	{
		transitions[i] = new bool*[spots_number];
		for (unsigned j=0;j<spots_number;j++)
			transitions[i][j] = new bool[5];	
	}	
	// stay (noop)
	for (unsigned s=0;s<spots_number;s++)
	{
		for (unsigned sp=0;sp<spots_number;sp++)
		{
			if (s==sp)
			{
				transitions[s][sp][0] = true;
			}
			else
			{
				transitions[s][sp][0] = false;
			}
		}
	}
	// North
	for (unsigned s=0;s<spots_number;s++)
	{
		for (unsigned sp=0;sp<spots_number;sp++)
		{
			transitions[s][sp][1]=false;
		}
	}
	for (unsigned s=0;s<spots_number;s++)
	{
		if (walls_around[s][1])
		{
			transitions[s][s][1] = true;
		}
		else 
		{
			transitions[s][s+grid_side_size][1] = true;
		}
	}
	// East
	for (unsigned s=0;s<spots_number;s++)
	{
		for (unsigned sp=0;sp<spots_number;sp++)
		{
			transitions[s][sp][2] = false;
		}
	}
	for (unsigned s=0;s<spots_number;s++)
	{
		if (walls_around[s][3])
		{
			transitions[s][s][2] = true;
		}
		else 
		{
			transitions[s][s+1][2] = true;
		}
	}
	// South
	for (unsigned s=0;s<spots_number;s++)
	{
		for (unsigned sp=0;sp<spots_number;sp++)
		{
			transitions[s][sp][3] = false;
		}
	}
	for (unsigned s=0;s<spots_number;s++)
	{
		if (walls_around[s][5])
		{
			transitions[s][s][3] = true;
		}
		else 
		{
			transitions[s][s-grid_side_size][3] = true;
		}
	}
	// West
	for (unsigned s=0;s<spots_number;s++)
	{
		for (unsigned sp=0;sp<spots_number;sp++)
		{
			transitions[s][sp][4]=false;
		}
	}
	for (unsigned s=0;s<spots_number;s++)
	{
		if (walls_around[s][7])
		{
			transitions[s][s][4] = true;
		}
		else 
		{
			transitions[s][s-1][4] = true;
		}
	}
//	plot_transitions(transitions,spots_number, spots_number, 5);
	return transitions;
}


/*******************************************************************************
	Write the probabilities of observations 
	in the RDDL file describing the POMDP.
********************************************************************************/
void generate_CPFS_Observations_rddl_file(std::ofstream & rddl_file, unsigned grid_side_size, unsigned spots_number, double CM, double DM)
{
	rddl_file << "\n\n	cpfs {" << std::endl;
	double gss = (double) grid_side_size;
	unsigned nbBooleans = ceil(log2(spots_number));

	///////////////////
	// robot-loc-obs
	///////////////////
	rddl_file << std::endl;
	rddl_file << "		// Observations" << std::endl;
	for (unsigned i=0;i<nbBooleans;i++)
		rddl_file << "		robot-loc-obs-" << i <<  " = KronDelta(robot-loc-state-" << i << "');" << std::endl; 
	rddl_file << std::endl;

	///////////////////
	// obs-target-one
	///////////////////
	rddl_file << "		obs-target-one ="; 

	//coordinate of the North East Target (T1)
  	double T_x1 = 0.0;
  	double T_y1 = gss-1.0;

	// observation probability of the first target for each robot location
	for (unsigned s=0;s<spots_number;s++)
	{
		//////////////////////////
		//			//		
		//  Y (N)		//
		//			//
		//  ^ 			//
		//  |			//
		//  |			//
		//  |       		//
		//  0------->   X (W)	//
		//			//
		//////////////////////////
		
		double y = s/grid_side_size;
		double x = s%grid_side_size;

		// distance between the robot location and T1
	  	double d1 = sqrt(pow(T_x1-x,2.0) + pow(T_y1-y,2.0));
	  
// TODO et si pas symétrique? 
// p(c1) != p(c2)?
		// correct observation of T1
		double p_c1 = 0.5*(1.0+CM*exp(-d1/DM)); // p_correct_T1
		
// TODO gérer pour avoir ">0" = "0.0000000001" et non "100000e-10" ou "0" 
// KronDelta(true) et pas Bernoulli(1) 
// KronDelta(false) et pas Bernoulli(0)

		// prec 10^-10
		p_c1 = floor(10000000000.0*p_c1)/10000000000.0;
		double p_w1 = 1.0000000000 -p_c1;

		// robot location in terms of boolean variable assignments
		char* yesNo = new char[nbBooleans];
		for (unsigned boolIndex=0;boolIndex<nbBooleans;boolIndex++)
		{
			if ((s & ( 1 << boolIndex )) >> boolIndex)
			{
				yesNo[boolIndex] = ' ';
			}
			else
			{
				yesNo[boolIndex] = '~';
			}		
		}
		// first location
		if (s==0)
		{
			rddl_file << " if (";
		}
		else
		{
			rddl_file << "				else if (";
		}
		if (s<spots_number-1)
		{
			// targetOneIsA
			rddl_file << " targetOneIsA'";
			for (unsigned i=0;i<nbBooleans-1;i++)
			{
				rddl_file << " ^ " << yesNo[i] << "robot-loc-state-" << i << "'";
			}
			rddl_file << " ^ " << yesNo[nbBooleans-1] << "robot-loc-state-" << nbBooleans-1 << "'" << " )" << std::endl;
			if ( (p_c1>0) && (p_c1<1) )
			{
				rddl_file << "					then Bernoulli("<< p_c1 <<")" << std::endl;
			}
			else if (p_c1>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}

			// ~targetOneIsA
			rddl_file << "				else if ( ~targetOneIsA' ";
			for (unsigned i=0;i<nbBooleans-1;i++)
			{
				rddl_file << " ^ " << yesNo[i] << "robot-loc-state-" << i << "'";
			}
			rddl_file << " ^ " << yesNo[nbBooleans-1] << "robot-loc-state-" << nbBooleans-1 << "'" << " )" << std::endl;
			if ( (p_w1>0) && (p_w1<1) )
			{
				rddl_file << "					then Bernoulli("<< p_w1 <<")" << std::endl;
			}	
			else if (p_w1>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}
		}
		else
		{
			// targetOneIsA
			rddl_file << " targetOneIsA'";
			for (unsigned i=0;i<nbBooleans-1;i++)
			{
				rddl_file << " ^ " << yesNo[i] << "robot-loc-state-" << i << "'";
			}
			rddl_file << " ^ " << yesNo[nbBooleans-1] << "robot-loc-state-" << nbBooleans-1 << "'" << " )" << std::endl;
			if ( (p_c1>0) && (p_c1<1) )
			{
				rddl_file << "					then Bernoulli("<< p_c1 <<")" << std::endl;
			}
			else if (p_c1>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}
			// ~targetOneIsA
			if ( (p_w1>0) && (p_w1<1) )
			{
				rddl_file << "				else\n					Bernoulli("<< p_w1 <<");" << std::endl;
			}
			else if (p_w1>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}
		}
		delete [] yesNo;
	}


	///////////////////
	// obs-target-two
	///////////////////
	rddl_file << "		obs-target-two ="; 
	//coordinate of the South West Target (T2)
  	double T_x2 = gss-1.0;
  	double T_y2 = 0.0;

	// observation probability of the second target for each robot location
	for (unsigned s=0;s<spots_number;s++)
	{	
		double y = s/grid_side_size;
		double x = s%grid_side_size;

		// distance between the robot location and T2
	  	double d2 = sqrt(pow(T_x2-x,2.0) + pow(T_y2-y,2.0));

		// correct observation of T2		
		double p_c2 = 0.5*(1.0+CM*exp(-d2/DM)); 
		p_c2 = floor(10000000000.0*p_c2)/10000000000.0;
		// wrong observation of T2
		double p_w2 = 1.0000000000 -p_c2; 

		// robot location in terms of boolean variable assignments
		char* yesNo = new char[nbBooleans];
		for (unsigned boolIndex=0;boolIndex<nbBooleans;boolIndex++)
		{
			if ((s & ( 1 << boolIndex )) >> boolIndex)
			{
				yesNo[boolIndex] = ' ';
			}
			else
			{
				yesNo[boolIndex] = '~';
			}		
		}
		// first location
		if (s==0)
		{
			rddl_file << " if (";
		}
		else
		{
			rddl_file << "				else if (";
		}
		if (s<spots_number-1)
		{
			// targetOneIsA
			rddl_file << " targetOneIsA'";
			for (unsigned i=0;i<nbBooleans-1;i++)
			{
				rddl_file << " ^ " << yesNo[i] << "robot-loc-state-" << i << "'";
			}
			rddl_file << " ^ " << yesNo[nbBooleans-1] << "robot-loc-state-" << nbBooleans-1 << "'" << " )" << std::endl;
			if ( (p_c2>0) && (p_c2<1) )
			{
				rddl_file << "					then Bernoulli("<< p_c2 <<")" << std::endl;
			}
			else if (p_c2>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}
			// ~targetOneIsA
			rddl_file << "				else if ( ~targetOneIsA' ";
			for (unsigned i=0;i<nbBooleans-1;i++)
			{
				rddl_file << " ^ " << yesNo[i] << "robot-loc-state-" << i << "'";
			}
			rddl_file << " ^ " << yesNo[nbBooleans-1] << "robot-loc-state-" << nbBooleans-1 << "'" << " )" << std::endl;
			if ( (p_w2>0) && (p_w2<1) )
			{
				rddl_file << "					then Bernoulli("<< p_w2 <<")" << std::endl;
			}
			else if (p_w2>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}	
		}
		else
		{
			// targetOneIsA
			rddl_file << " targetOneIsA'";
			for (unsigned i=0;i<nbBooleans-1;i++)
			{
				rddl_file << " ^ " << yesNo[i] << "robot-loc-state-" << i << "'";
			}
			rddl_file << " ^ " << yesNo[nbBooleans-1] << "robot-loc-state-" << nbBooleans-1 << "'" << " )" << std::endl;
			if ( (p_c2>0) && (p_c2<1) )
			{
				rddl_file << "					then Bernoulli("<< p_c2 <<")" << std::endl;
			}
			else if (p_c2>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}
			// ~targetOneIsA
			if ( (p_w2>0) && (p_w2<1) )
			{
				rddl_file << "				else\n					Bernoulli("<< p_w2 <<");" << std::endl;
			}
			else if (p_w2>0)
			{
				rddl_file << "					then KronDelta(true)" << std::endl;
			}
			else
			{
				rddl_file << "					then KronDelta(false)" << std::endl;
			}
		}
		delete [] yesNo;
	}
}


/*******************************************************************************
	Write the header of the RDDL file defining the POMDP problem.
********************************************************************************/
void generate_header_rddl_file(std::ofstream & rddl_file)
{
	rddl_file << "/////////////////////////////////////////////////////////////////////" << std::endl;
	rddl_file << "//" << std::endl;
	rddl_file << "// reach the good target POMDP" << std::endl;
	rddl_file << "//" << std::endl;
	rddl_file << "// Author: Nicolas Drougard (nicolas.drougard [at] onera.fr)" << std::endl;
	rddl_file << "//" << std::endl;
	rddl_file << "// In a grid, a robot (R) has to reach a target (A)." << std::endl;
	rddl_file << "// Successive locations of (R) are fully observable." << std::endl;
	rddl_file << "// Two targets, T1 and T2 are in this grid," << std::endl;
	rddl_file << "// and their locations are known." << std::endl;
	rddl_file << "// One target is (A), and the other is (B)." << std::endl;
	rddl_file << "// However, the targets natures, (A) or (B), are unknown," << std::endl;
	rddl_file << "// and the aim is to reach target (A)." << std::endl;
	rddl_file << "// At each process step, the robot analyzes pictures of each target" << std::endl;
	rddl_file << "// which produces a guess for targets natures:" << std::endl;
	rddl_file << "// the two targets can be observed as (A), which corresponds to the observation (oAA)" << std::endl;
	rddl_file << "// or only T1 (oAB), or only T2 (oBA) or no target (oBB)." << std::endl;
	rddl_file << "//" << std::endl;
	rddl_file << "// As an ignorant prior, the initial belief b_0 is: p(T1=A) = p(T2=A) = 0.5" << std::endl;
	rddl_file << "//" << std::endl;
	rddl_file << "// * * * * * * * * * * * *" << std::endl;
	rddl_file << "// * T1                  *" << std::endl;
	rddl_file << "// *                     *" << std::endl;
	rddl_file << "// *                     *" << std::endl;
	rddl_file << "// *                     *" << std::endl;
	rddl_file << "// *                     *" << std::endl;
	rddl_file << "// *                     *" << std::endl;
	rddl_file << "// *                     *" << std::endl;
	rddl_file << "// * R                T2 *" << std::endl;
	rddl_file << "// * * * * * * * * * * * *" << std::endl;
	rddl_file << "//" << std::endl;
	rddl_file << "/////////////////////////////////////////////////////////////////////" << std::endl;
	rddl_file << std::endl;
	rddl_file << "domain RTGT_pomdp {" << std::endl;
	rddl_file << "	requirements = {" << std::endl;
	rddl_file << "		reward-deterministic," << std::endl;
	rddl_file << "		partially-observed" << std::endl;
	rddl_file << "	};" << std::endl;
	rddl_file << std::endl;
}


/*******************************************************************************
	Read and store parameters in parameters.txt.
********************************************************************************/
void parse_parameters(unsigned &grid_size, unsigned &nb_walls, double &discount_factor, unsigned &DM, unsigned &CM, bool &well_loaded)
{
	std::string word;
	std::ifstream params("parameters.txt",std::ios::in);
	if (params)
	{
		// grid size
		params >> word;
		well_loaded = (well_loaded && (word=="grid_size:"));
		params >> grid_size;
		
		// number of walls 		
		params >> word;
		well_loaded = (well_loaded && (word=="walls_number:"));
		params >> nb_walls;
		if (nb_walls >= grid_size*grid_size-2)
		{
			nb_walls=0;
			std::cerr << "WARNING! walls_number has to be lower than grid_size^2-1\n ==>> walls_number set to 0." << std::endl;
		}

		// MDP discount factor
		params >> word;
		well_loaded = (well_loaded && (word=="discount:"));
		params >> discount_factor;
		if ( (discount_factor<0) || (discount_factor>1) )
		{
			std::cerr << "WARNING! discount has to be positive and not greater than one\n ==>> discount set to 0.99." << std::endl;
		}

		// D parameter of the observation model
		params >> word;
		well_loaded = (well_loaded && (word=="DM:"));
		params >> DM;
		if (DM <=0 )
		{
			DM=1.0;
			std::cerr << "WARNING! parameter DM has to be positive\n ==>> DM set to 1.0." << std::endl;
		}

		// C parameter of the observation model
		params >> word;
		well_loaded = (well_loaded && (word == "CM:"));
		params >> CM;
		if (CM<0 || CM >1)
		{
			CM=1.0;
			std::cerr << "WARNING! parameter CM has to be between 0 and 1\n ==>> CM set to 1.0." << std::endl;
		}
	}
	else
	{
		std::cerr << "WARNING! problem opening the parameters file" << std::endl;
	}
}


/*******************************************************************************
	Returns an array nb_spots*8 describing for each location, 
	the walls around the current spot, in the following way:

	0 -> 1 -> 2
	^         v
	7    s    3
	^         v
	6 <- 5 <- 4

	where s is the location (spot).
********************************************************************************/
bool** walls_loc2description(bool* walls_locations, unsigned grid_side_size)
{
	unsigned number_of_spots = grid_side_size*grid_side_size;
	bool** walls_around = new bool*[number_of_spots];

	for (unsigned i=0;i<number_of_spots;i++)
		walls_around[i] = new bool[8];

	for (unsigned s=0;s<number_of_spots;s++)
	{
		for (unsigned j=0;j< 8 ; j++)
		{
			walls_around[s][j]=false;
		}
		int is = s;
		int igrid_size = grid_side_size;
		if (is+igrid_size-igrid_size*igrid_size+1>0) // north edge?
		{
			walls_around[s][0]=true;
			walls_around[s][1]=true;
			walls_around[s][2]=true;
		}
		if (is%igrid_size == igrid_size-1) // east edge?
		{
			walls_around[s][2]=true;
			walls_around[s][3]=true;
			walls_around[s][4]=true;
		}
		if (is-igrid_size<0) // south edge?
		{
			walls_around[s][4]=true;
			walls_around[s][5]=true;
			walls_around[s][6]=true;
		}
		if (is%igrid_size==0) // west edge?
		{
			walls_around[s][6]=true;
			walls_around[s][7]=true;
			walls_around[s][0]=true;
		}


		for (unsigned j=0;j<3;j++)
		{
			if (!walls_around[s][j])
			{
				walls_around[s][j] = walls_locations[s+grid_side_size-1+j];   
			} 
		}
		for (unsigned j=0;j<3;j++)
		{
			if (!walls_around[s][j+2])
			{
				walls_around[s][j+2] = walls_locations[s+(1-j)*grid_side_size+1];   
			} 
		}
		for (unsigned j=0;j<3;j++)
		{
			if (!walls_around[s][j+4])
			{
				walls_around[s][j+4] = walls_locations[s-grid_side_size+1-j];
			}
		}
		for (unsigned j=0;j<2;j++)
		{
			if (!walls_around[s][j+6])
			{
				walls_around[s][j+6] = walls_locations[s+(j-1)*grid_side_size-1];
			} 
		}
	}

	return walls_around;
}


/*******************************************************************************
	Save the used grid in grid.txt with walls represented by letter "X",
	using the table walls_locations.
	Save as well the table walls_around_spot in the same file. 
********************************************************************************/
void save_grid(bool** walls_around_spot, bool* walls_locations, unsigned grid_side_size)
{
	unsigned spots_number = grid_side_size*grid_side_size;
	std::ofstream grid("grid.txt", std::ios::out | std::ios::trunc );
	if (grid)
	{ 
		grid << "walls_loc: " << std::endl;
		for (unsigned s=0;s<spots_number;s++)
		{
			grid << walls_locations[s] << " ";
		}
		grid << std::endl;
		grid << "walls_around: " << std::endl;
		for (unsigned s=0;s<spots_number;s++)
		{ 
			for (unsigned i=0;i<8;i++)
			{
				grid << walls_around_spot[s][i] << " ";
			}
			grid << std::endl;      
		}
		grid << std::endl;      
		grid << "for each position s we describe the walls around" << std::endl;
		grid << "0---1-->2" << std::endl;
		grid << "|       |" << std::endl;
		grid << "7   s   3" << std::endl;
		grid << "|       |" << std::endl;
		grid << "6<--5---4" << std::endl; 
		grid << std::endl;
		grid << "grid (X=wall):  " << std::endl;      
		std::cout << "grid (X=wall):  " << std::endl;   
		std::cout << "   " ;
		grid << "   " ;
		for (unsigned c=0;c<grid_side_size;c++)
		{
			grid <<  "  X " ;
			std::cout << "  X " ;
		}
		grid << "\n";
		std::cout << "\n";
     
		for (unsigned l=0;l<grid_side_size;l++)
		{
			grid <<  "   "; 
			std::cout << "   ";
			for (unsigned c=0;c<grid_side_size;c++)
			{
				grid  << "----";
				std::cout << "----" ;
			}
			grid << "\n X ";
			std::cout << "\n X ";
			for (unsigned c=0;c<grid_side_size;c++)
			{
				if (walls_locations[(grid_side_size-l-1)*grid_side_size+c])
				{
					grid << "| X ";
					std::cout << "| X " ;
				}
				else
				{
					grid <<  "|   " ;
					std::cout << "|   " ;
				}
			}
			grid <<  "| X \n";
			std::cout << "| X \n";
		}
		grid  << "   "; 
		std::cout << "   ";
		for (unsigned c=0;c<grid_side_size;c++)
		{
			grid <<  "----" ;
			std::cout << "----" ;
		}
		grid <<  "\n   ";
		std::cout << "\n   ";
		for (unsigned c=0;c<grid_side_size;c++)
		{
			grid  << "  X " ;
			std::cout << "  X " ;
		}
		grid  << "\n"; 
		std::cout << "\n";
      
		grid.close();
	}
	else
	{
		std::cerr << "WARNING! problem writting in grid.txt." << std::endl;
	}
}

