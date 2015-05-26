/**
 * RDDL: This policy is for use with the Symbolic Perseus and SPUDD
 *       translations.  The planner displays state (MDP) or observations
 *       (POMDP) as true fluents as well as possible actions and
 *       then randomly takes an action.  State-action constraints are
 *       not checked.
 * 
 * @author Scott Sanner (ssanner [at] gmail.com)
 * @version 12/18/10
 *
 **/

package rddl.policy;

import java.io.File;
import java.util.*;

import javax.crypto.interfaces.PBEKey;

import jdd.*;

import rddl.*;
import rddl.RDDL.*;
import rddl.parser.parser;
import rddl.ppudd.POPPUDDCUDD;
import rddl.translate.RDDL2Format;

import rddl.sim.*;
import util.Pair;

///////////////////////////////////////////////////////////////////////////
//                             Helper Functions
///////////////////////////////////////////////////////////////////////////

public class POPPUDD2policy extends Policy {
	
	public final static boolean SHOW_STATE   = true;
	public final static boolean SHOW_ACTIONS = true;
	public final static boolean SHOW_ACTION_TAKEN = true;
	//public final static boolean ALLOW_NOOP   = false;
	
	// Just use the default random seed
	public Random _rand = new Random();
	
	public String instanceName;
	public JDDNode deltaPOL;
	public Hashtable<Integer,JDDNode> ind_TRANS_BELIEF;
	
	public int compteur;
	public boolean firstTrial;
	
	public POPPUDDCUDD leContexte;
	
	public ArrayList<String> lesActions;
	public String previousAction;
	public JDDNode indicatriceCurrentBelief;
	private JDDNode current_belief;
	
	public JDDVars stateVars;
	public JDDVars primedObservationVars;
	public JDDVars observationVars;
	
	public POPPUDD2policy () {}
	
	public POPPUDD2policy(String instance_name) {
		
		super(instance_name);
		instanceName = instance_name;
		firstTrial = true;
		ind_TRANS_BELIEF = new Hashtable<Integer,JDDNode>();
		compteur = 0;
	}

	public JDDNode indicAssignBel(JDDNode aTree, JDDNode treeCollector) 
	{
		JDD.Ref(aTree); 
		JDD.Ref(treeCollector); 
		JDDNode difference = JDD.Apply(JDD.MINUS, aTree, treeCollector); // diff = belief - ADD_beta // ET LA
		JDD.Ref(difference);
		JDDNode isPos = JDD.GreaterThanEquals(difference, 0.0d); // isPos =  1_{diff>=0}
		JDD.Ref(difference);
		JDDNode isStrictNeg = JDD.LessThan(difference, 0.0d); // isStrictNeg = 1_{diff<0}
		JDD.Ref(difference);
		JDDNode temp = JDD.Apply(JDD.TIMES, difference, isStrictNeg); // diff*1_{diff<0}
		difference = JDD.Apply(JDD.TIMES, difference, isPos); // diff*1_{diff>=0}
		difference = JDD.Apply(JDD.MINUS, difference, temp); // abs(diff) // MINUS!!!!!!
		difference = JDD.MaxAbstract(difference, stateVars);
		JDDNode resultat = JDD.LessThanEquals(difference, 0d); // [difference killed]
		return resultat;
	}
	
	static void afficheDdNodeMDP(JDDNode dd, int prof, char TE) {
		int index = dd.getIndex();
		if (dd.isConstant() == true && TE == 't') {
			System.out.print("  ");
			System.out.print("val: " + dd.getValue() + " ");
			System.out.println();
		} else if (dd.isConstant() == true && TE == 'e') {
			System.out.print("  ");
			for (int i = 0; i < prof; i++) {
				System.out.print("        ");
			}
			System.out.print("val: " + dd.getValue() + " ");
			System.out.println();
		} else if (dd.isConstant() == false && TE == 't') {
			System.out.print("  ");
			System.out.print("id: " + index + " ");
			afficheDdNodeMDP(dd.getThen(), prof + 1, 't');
			afficheDdNodeMDP(dd.getElse(), prof + 1, 'e');

		} else {
			System.out.print("  ");
			for (int i = 0; i < prof; i++) {
				System.out.print("        ");
			}
			System.out.print("id: " + index + " ");
			afficheDdNodeMDP(dd.getThen(), prof + 1, 't');
			afficheDdNodeMDP(dd.getElse(), prof + 1, 'e');
		}
	}
	static public void afficheDdNodeMDP(JDDNode ADD)
	{
		afficheDdNodeMDP(ADD, 0, 't');
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	//                      Main Action Selection Method
	//
	// If you're using Java and the SPUDD / Symbolic Perseus Format, this 
	// method is the only client method you need to understand to implement
	// your own custom policy.
	///////////////////////////////////////////////////////////////////////////

public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {
	
		if (s == null) { 
			System.out.println("\n\n\nNO STATE/OBS: taking noop\n\n");
			previousAction = "noop";
			return new ArrayList<PVAR_INST_DEF>();
		}
		
		String fluent_type = s._alObservNames.size() > 0 ? "observ" : "states";
		TreeSet<String> true_vars = getTrueFluents(s, fluent_type);
		if (SHOW_STATE) {
			System.out.println("\n==============================================");
			System.out.println("\nTrue " + 
					           (fluent_type.equals("states") ? "state" : "observation") + 
							   " variables:");
			for (String prop_var : true_vars)
				System.out.println(" - " + prop_var);
		}
		
		// Get a map of { legal action names -> RDDL action definition }  
		Map<String,ArrayList<PVAR_INST_DEF>> action_map = ActionGenerator.getLegalBoolActionMap(s);
		
		if (SHOW_STATE) {
			System.out.println("\nLegal action names:");
			for (String action_name : action_map.keySet())
				System.out.println(" - " + action_name);
		}
		
		HashMap<String,Boolean> var2assign = new HashMap<String,Boolean>();
		for (PVAR_NAME p : (ArrayList<PVAR_NAME>)s._hmTypeMap.get(fluent_type)) {	
			try {
				// Go through all term groundings for variable p
				ArrayList<ArrayList<LCONST>> gfluents = s.generateAtoms(p);										
				for (ArrayList<LCONST> gfluent : gfluents) {
					if ((Boolean)s.getPVariableAssign(p, gfluent)) {
						var2assign.put(RDDL2Format.CleanFluentName(p._sPVarName + gfluent), true);
					}
					else
					{
						var2assign.put(RDDL2Format.CleanFluentName(p._sPVarName + gfluent), false);
					}
				}
			} catch (Exception ex) {
				System.err.println("polPOPPUDD: could not retrieve assignment for " + p + "\n");
			}
		}
		
		// compute indication function of current observation
		JDDNode indicatricePrimedObservation = JDD.Constant(1d);
		for (int i=0;i<observationVars.getNumVars();i++)
		{
			if (var2assign.get(leContexte.pb._ind2ObsvarName.get(observationVars.getVarIndex(i))))
			{
				JDD.Ref(primedObservationVars.getVar(i));
				indicatricePrimedObservation = JDD.Apply(JDD.TIMES,indicatricePrimedObservation, primedObservationVars.getVar(i));
			}
			else
			{
				JDD.Ref(primedObservationVars.getVar(i));
				JDDNode nonVar = JDD.Apply(JDD.MINUS,JDD.Constant(1d), primedObservationVars.getVar(i));
				indicatricePrimedObservation = JDD.Apply(JDD.TIMES,indicatricePrimedObservation, nonVar);
			}
		}

		 // belief indic computation
		System.out.println("indicatriceCurrentBelief");
		afficheDdNodeMDP(indicatriceCurrentBelief);
		
		JDD.Ref(indicatriceCurrentBelief); // a enlever
		JDDNode indic = JDD.Apply(JDD.TIMES,indicatricePrimedObservation, indicatriceCurrentBelief); // [args killed] // TODO and
		
		int laction = lesActions.indexOf(previousAction);
		JDD.Ref(ind_TRANS_BELIEF.get(laction-1));
		// computation of the indicator function of the new belief 
		JDDNode indicNewBelief = JDD.Apply(JDD.TIMES,ind_TRANS_BELIEF.get(laction-1), indic); // [indic killed]
		indicNewBelief = JDD.MaxAbstract(indicNewBelief, leContexte.BeliefVars);
		indicNewBelief = JDD.MaxAbstract(indicNewBelief, primedObservationVars);
		indicNewBelief = JDD.SwapVariables(indicNewBelief, leContexte.PrimedBeliefVars, leContexte.BeliefVars );
		System.out.println("\nindicNewBelief");
		afficheDdNodeMDP(indicNewBelief); 
		
		JDD.Ref(leContexte.ADD_beta);
		JDD.Ref(indicNewBelief);
		JDDNode newB = JDD.Apply(JDD.TIMES, indicNewBelief,leContexte.ADD_beta); // // TODO TODO TODO AND!!!! 
		newB = JDD.MaxAbstract(newB, leContexte.BeliefVars );
		
		// belief update
		JDD.Deref(current_belief); 
		current_belief = newB; 
		
		// action extraction
		JDD.Ref(deltaPOL);
		JDD.Ref(indicNewBelief);
		JDDNode action = JDD.Apply(JDD.TIMES, deltaPOL, indicNewBelief);  
		action = JDD.MaxAbstract(action, leContexte.BeliefVars);	
		
		// returned action
		laction = (int) action.getValue();
		String bestaction = lesActions.get(laction);
		System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n ACTION num " + compteur + " :");
		System.out.println(bestaction);
		System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
		
		// update indication function of the belief
		JDD.Deref(indicatriceCurrentBelief); 
		indicatriceCurrentBelief = indicNewBelief; 
		JDD.Deref(action);
		compteur++;
		
		previousAction = bestaction; // will be used for the belief update during next iteration
		return action_map.get(bestaction);
	}
	

	///////////////////////////////////////////////////////////////////////////
	//                             Trial Signals
	//
	// If you need to keep track of state information across rounds or sessions, 
	// just modify these methods.  (Each session consists of total_rounds rounds.)
	///////////////////////////////////////////////////////////////////////////

	public void roundInit(double time_left, int horizon, int round_number, int total_rounds) {
		System.out.println("\n*********************************************************");
		System.out.println(">>> ROUND INIT " + round_number + "/" + total_rounds + "; time remaining = " + time_left + ", horizon = " + horizon);
		System.out.println("*********************************************************");
		if (firstTrial)
		{
			firstTrial = false;
			long mem = 6000*1024;
			double eps = 0.00000001;
			System.out.println("\n\n^^^^^^^^^^^^^^^^^^^^^\n INIT CUDD TESTPO \nvvvvvvvvvvvvvvvvvvvvv\n\n");
			JDD.InitialiseCUDD(mem,eps);
			
			try{
				// WARNING!! NAME OF DIRECTORY WRITTEN HERE
				leContexte = new POPPUDDCUDD("spudd_sperseus/" + instanceName + ".sperseus");
				leContexte.initPOPPUDD();
				lesActions = leContexte._alActions;

				System.out.println("\n\nComputations begins\n\n");
				leContexte.POPPUDDinit();
				leContexte.POPPUDD();
				System.out.println("\n\nend of computations\n\n");
				
				System.out.println(leContexte.pb.actNames);
				deltaPOL = leContexte.delta;

				for (int act=0;act<leContexte.pb.actNames.size();act++)
				{
					System.out.println("action: " + act);
					ind_TRANS_BELIEF.put(act, leContexte.ind_BAOPBP.get(act));
				}
				
				current_belief = JDD.Constant(1d); 
				indicatriceCurrentBelief = JDD.Constant(1d);
				
				stateVars = leContexte.pb.vars;
				primedObservationVars = leContexte.pb.primedObsvar;
				observationVars = leContexte.pb.obsvar;
				stateVars.refAll();
				primedObservationVars.refAll();
				observationVars.refAll();
				leContexte.derefPOPPUDD();
			}catch (Exception g) {
				System.out.println("Exception: " + g);
				g.printStackTrace();
			}
		}
		JDD.Deref(current_belief);
		current_belief = leContexte.beliefInit;
		JDD.Ref(current_belief);
		JDD.Deref(indicatriceCurrentBelief);
		indicatriceCurrentBelief = indicAssignBel(current_belief, leContexte.ADD_beta);
	}
	
	public void roundEnd(double reward) {
		System.out.println("\n*********************************************************");
		System.out.println(">>> ROUND END, reward = " + reward);
		System.out.println("*********************************************************");
	}
	
	public void sessionEnd(double total_reward) {
		System.out.println("\n*********************************************************");
		System.out.println(">>> SESSION END, total reward = " + total_reward);
		System.out.println("*********************************************************");
		
		JDD.Deref(indicatriceCurrentBelief);
		JDD.Deref(deltaPOL);
		for (int act=0;act<this.lesActions.size()-1;act++)
		{
			JDD.Deref(leContexte.pi_OP_BelA.get(act));
			JDD.Deref(ind_TRANS_BELIEF.get(act));
		}
		JDD.Deref(current_belief);
		JDD.Deref(leContexte.beliefInit);
		JDD.Deref(leContexte.ADD_beta);
		JDD.Deref(leContexte.beliefsMask);
		
		for (int var = 0; var < leContexte.BeliefVars.getNumVars(); var++) {
			JDD.Deref(leContexte.BeliefVars.getVar(var)); // TODO derefAll!
			JDD.Deref(leContexte.PrimedBeliefVars.getVar(var));
		}
		stateVars.derefAll();
		primedObservationVars.derefAll();
		observationVars.derefAll();
		System.out.println("CLOSE CUDD");
		JDD.CloseDownCUDD();
		System.out.println("CUDD CLOSED");
	}

	///////////////////////////////////////////////////////////////////////////
	//                             Helper Methods
	//
	// You likely won't need to understand the code below, only the above code.
	///////////////////////////////////////////////////////////////////////////
	
	public TreeSet<String> getTrueFluents(State s, String fluent_type) {
				
		// Go through all variable types (state, interm, observ, action, nonfluent)
		TreeSet<String> true_fluents = new TreeSet<String>();
		for (PVAR_NAME p : (ArrayList<PVAR_NAME>)s._hmTypeMap.get(fluent_type)) {
			
			try {
				// Go through all term groundings for variable p
				ArrayList<ArrayList<LCONST>> gfluents = s.generateAtoms(p);										
				for (ArrayList<LCONST> gfluent : gfluents) {
					if ((Boolean)s.getPVariableAssign(p, gfluent)) {
						true_fluents.add(RDDL2Format.CleanFluentName(p._sPVarName + gfluent));
					}
				}
			} catch (Exception ex) {
				System.err.println("SPerseusSPUDDPolicy: could not retrieve assignment for " + p + "\n");
			}
		}
				
		return true_fluents;
	}

	public String getStateDescription(State s) {
		StringBuilder sb = new StringBuilder();
		
		// Go through all variable types (state, interm, observ, action, nonfluent)
		for (Map.Entry<String,ArrayList<PVAR_NAME>> e : s._hmTypeMap.entrySet()) {
			
			if (e.getKey().equals("nonfluent"))
				continue;
			
			// Go through all variable names p for a variable type
			for (PVAR_NAME p : e.getValue()) {
				sb.append(p + "\n");
				try {
					// Go through all term groundings for variable p
					ArrayList<ArrayList<LCONST>> gfluents = s.generateAtoms(p);										
					for (ArrayList<LCONST> gfluent : gfluents)
						sb.append("- " + e.getKey() + ": " + p + 
								(gfluent.size() > 0 ? gfluent : "") + " := " + 
								s.getPVariableAssign(p, gfluent) + "\n");
						
				} catch (EvalException ex) {
					sb.append("- could not retrieve assignment " + s + " for " + p + "\n");
				}
			}
		}
				
		return sb.toString();
	}

}
