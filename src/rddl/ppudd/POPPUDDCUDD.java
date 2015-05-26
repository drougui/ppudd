package rddl.ppudd;

import java.util.*;

import rddl.parser.*; // parseur ParseSpuddCUDD
import util.*;

import jdd.*; // cudd

import java.lang.Math;

import javax.jws.soap.InitParam;

import com.sun.org.apache.bcel.internal.generic.LADD;

public class POPPUDDCUDD extends DebugJDD { // in debug mode to see reference problems

	public ArrayList<String> _alActions; // for the strategy delta,
										// the index of an action 
										// classical index (for transitions/observations/reward)
										// +1
	public String spudd_sperseus_file;
	public ParseCUDD pb;
	public JDDNode ZEROD;
	public JDDNode ONED;
	//private String[] visibleStates;

	public HashMap<Pair<String, String>, JDDNode> ADDtransFunc;
	public HashMap<Pair<String, String>, JDDNode> ADDobsFunc;
	private HashMap<String, JDDNode> action2pref;
	public Vector<Double> qualScale;
	public JDDNode delta;
	public JDDNode ADD_beta;
	private JDDNode mu;
	
	public Hashtable<Integer,JDDNode> ind_BAOPBP;
	public JDDNode beliefInit;
	public Hashtable<Integer,JDDNode> pi_OP_BelA;
	public JDDVars BeliefVars;
	public JDDVars PrimedBeliefVars;
	public JDDNode beliefsMask;
	
	public String[] visibleStateVars;
	public Vector<String> ObsVarNames_VisibleStateVars;
	public TreeMap<String, String> ObsVarNames_TO_VisibleStateVarNames;
	public String[] hiddenStateVars;
	
	HashMap<String, JDDNode> action2rewardFunction = new HashMap<String, JDDNode>();

	public POPPUDDCUDD() {
	}

	public POPPUDDCUDD(String instance_name) /*throws Exception*/ {
		spudd_sperseus_file = instance_name;
		long mem = 6000 * 1024;
		double eps = 0.0000001;
		JDD.InitialiseCUDD(mem, eps); // tester avec d'autre mem jusqu'à ce
										// qu'il soit capable d'allouer
		// ON DECLARE ZERO ET UN EN double
		ZEROD = JDD.Constant(0d);
		ONED = JDD.Constant(1d);

		System.out.println("parse " + instance_name);
		pb = new ParseCUDD(instance_name);
		pb.parsePOMDP(false); // false = non fully observable

		_alActions = new ArrayList<String>();

		_alActions.add("rien"); // les actions commencent avec l'indice 1
		for (int i = 0; i < pb.actNames.size(); i++) {
			// System.out.println(action_name);
			String action_name = pb.actNames.get(i);
			_alActions.add(action_name);
			System.out.println("action " + i + " : " + pb.actNames.get(i));
		}

		ADDtransFunc = new HashMap<Pair<String, String>, JDDNode>();
		ADDobsFunc = new HashMap<Pair<String, String>, JDDNode>();
		action2pref = new HashMap<String, JDDNode>();
		qualScale = new Vector<Double>();

		ind_BAOPBP = new Hashtable<Integer,JDDNode>();	
		pi_OP_BelA = new Hashtable<Integer,JDDNode>();
		BeliefVars = new JDDVars();
		PrimedBeliefVars = new JDDVars();
		beliefInit = pb.init;
		JDD.Ref(beliefInit);
	}

	static void afficheDdNodeMDP(JDDNode dd, int prof, char TE) {
		int index = dd.getIndex();
		// int ref = (int) dd->ref;
		if (dd.isConstant() == true && TE == 't') {
			// System.out.println("if");
			System.out.print("  ");
			System.out.print("val: " + dd.getValue() + " ");
			System.out.println();
		} else if (dd.isConstant() == true && TE == 'e') {
			// System.out.println("else if 1");
			System.out.print("  ");
			for (int i = 0; i < prof; i++) {
				System.out.print("        ");
			}
			System.out.print("val: " + dd.getValue() + " ");
			System.out.println();
		} else if (dd.isConstant() == false && TE == 't') {
			// System.out.println("else if 2");
			System.out.print("  ");
			System.out.print("id: " + index + " ");
			afficheDdNodeMDP(dd.getThen(), prof + 1, 't');
			afficheDdNodeMDP(dd.getElse(), prof + 1, 'e');

		} else {
			// System.out.println("else");
			System.out.print("  ");
			for (int i = 0; i < prof; i++) {
				System.out.print("        ");
			}
			System.out.print("id: " + index + " ");
			afficheDdNodeMDP(dd.getThen(), prof + 1, 't');
			afficheDdNodeMDP(dd.getElse(), prof + 1, 'e');
		}
	}

	// initialization of the solver
	public void initPOPPUDD() {

		int nbActions = pb.actNames.size();
		System.out.println("\n\n =========== ");
		System.out.println("|| ACTIONS ||");
		System.out.println(" =========== ");
		for (int i = 0; i < nbActions; i++) { // the actual index of the action is i+1, 
											// for the strategy delta 
											// (but is i for actNames and ADDs of transition/observation/reward...)
			System.out.println("action " + i + " : " + pb.actNames.get(i));
		}

		System.out.println("\n\n =========== ");
		System.out.println("|| VARIABLES ||");
		System.out.println(" =========== ");

		// variables primées et non-primées
		pb._ind2VarName = new TreeMap<Integer, String>();
		for (int i = 0; i < pb.varNames.size(); i++) // for variables, the index begins with 0
		{
			pb._ind2VarName.put(i, pb.varNames.get(i));
			System.out.println("variable "
					+ pb._varName2Ind.get(pb.varNames.get(i)) + " : "
					+ pb.varNames.get(i));
		}

		this.qualScale.add(0d);
		this.qualScale.add(1d);

		System.out.println("\n\n =========== ");
		System.out.println("|| TRANS    ||");
		System.out.println(" =========== "); // transition ADDs are stored in ADDtransFunc

		for (int i = 0; i < pb.actNames.size(); i++) {
			System.out.println("trans functions for action "
					+ pb.actNames.get(i));
			for (int j = 0; j < pb.nStateVars; j++) {
				String variable = pb.varNames.get(j);
				String action_name = pb.actNames.get(i);
				Pair<String, String> theCouple = new Pair<String, String>(
						variable, action_name);
				JDDNode res = pb.possTrad(pb.actTransitions.get(i)[j]); 
				// possibility distributions are created
				// thanks to possTrad, without dereferencing the transition ADD
				JDD.Deref(pb.actTransitions.get(i)[j]);

				this.ADDtransFunc.put(theCouple, res);
				String str[] = JDD.GetTerminalsString(res).split(" ");
				for (int k = 0; k < str.length; k++) {
					double valeur = Double.parseDouble(str[k]);
					if (!this.qualScale.contains(valeur)) {
						this.qualScale.add(valeur);
					}
				}
			}
		}

		System.out.println("\n\n =========== ");
		System.out.println("|| REWARDS ||");
		System.out.println(" =========== ");

		// action REWARDS
		HashMap<String, JDDNode> action2reward = new HashMap<String, JDDNode>();
		JDDNode rewCUDD = ZEROD;

		System.out
				.println("\nlopp building rewards:\n rewCUDD:  ");
		DebugJDD.REFCOUNT(rewCUDD);
		System.out.println("\n");

		for (int i = 0; i < pb.actNames.size(); i++) {
			String action_name = pb.actNames.get(i);
			System.out.println("Reward for action " + action_name);
			JDD.Ref(pb.reward);
			rewCUDD = JDD.Apply(JDD.MINUS, pb.reward, pb.actCosts.get(i)); 
			// reward of an action is reward-cost

			action2reward.put(action_name, rewCUDD);
			DebugJDD.REFCOUNT(rewCUDD);
			System.out.println("");
		}
		JDD.Deref(pb.reward);

		System.out.println("\n\nDiscount and Horizon");
		System.out.println("discount " + pb.discount.getValue());
		System.out.println("horizon " + pb.horizon.getValue());

		System.out.println("\nINITIALIZATION:");

		//  preferencies creation 
		// (simple normalization between zero and one
		double rewMin = 1d;
		double rewMax = 0d;
		for (int i = 0; i < pb.actNames.size(); i++) {
			rewCUDD = action2reward.get(pb.actNames.get(i));
			// JDD.Ref(rewCUDD);
			rewMin = Math.min(JDD.FindMin(rewCUDD), rewMin);
			rewMax = Math.max(JDD.FindMax(rewCUDD), rewMax);
			System.out.println("rewMin: " + rewMin);
			System.out.println("rewMax: " + rewMax);
		}

		for (int i = 0; i < pb.actNames.size(); i++) {
			String action_name = pb.actNames.get(i);
			if (rewMin == rewMax) {
				JDD.Ref(ZEROD);
				this.action2pref.put(action_name, ZEROD);
			} else {
				JDDNode JDDrewMin = JDD.Constant(rewMin); 
				JDDNode JDDrewMax = JDD.Constant(rewMax); 

				JDD.Ref(JDDrewMin);
				JDDNode pref = JDD.Apply(JDD.MINUS,
						action2reward.get(action_name), JDDrewMin);

				JDDNode JDDrew2 = JDD.Apply(JDD.MINUS, JDDrewMax, JDDrewMin); 
				
				pref = JDD.Apply(JDD.DIVIDE, pref, JDDrew2);
				this.action2pref.put(action_name, pref);
			}
		}

		System.out.println("\n\n =========== ");
		System.out.println("|| OBSERV    ||");
		System.out.println(" =========== "); 
		// observation ADDs are stored is ADDobsFunc

		Boolean[] obsIdenticalToState = new Boolean[pb.nObsVars];
		// contains observations variables
		// which are in fact, 
		//almost surely equal to a state variable

		String[] obs2correspondingStateVarName = new String[pb.nObsVars]; 
		// for each index of the observation variables,
		// returns the name of the associated state variable.
																			
		for (int j = 0; j < pb.nObsVars; j++) {
			obsIdenticalToState[j] = true;
		}
		for (int i = 0; i < pb.actNames.size(); i++) {
			System.out
					.println("obs functions for action " + pb.actNames.get(i));
			for (int j = 0; j < pb.nObsVars; j++) {
				String variable = pb.obsvarNames.get(j);
				String action_name = pb.actNames.get(i);
				Pair<String, String> theCouple = new Pair<String, String>(
						variable, action_name);

				JDDNode res = pb.possTradO(pb.actObserve.get(i)[j]);
				JDD.Deref(pb.actObserve.get(i)[j]);
				this.ADDobsFunc.put(theCouple, res);

				String str[] = JDD.GetTerminalsString(res).split(" ");
				for (int k = 0; k < str.length; k++) {
					double valeur = Double.parseDouble(str[k]);
					if (!this.qualScale.contains(valeur)) {
						this.qualScale.add(valeur);
					}
				}

				// Visible variables?
				JDDNode support = JDD.GetSupport(res);
				Vector<String> leSupport = new Vector<String>();
				leSupport = pb.supportCube(support);
				JDD.Deref(support);
				if (leSupport.size() == 2) {
					JDDVars var0 = pb.obsvarName2obsvar.get(leSupport.get(0)); // arbitrary
																				// initialization
					JDDVars var1 = pb.varName2var.get(leSupport.get(1));

					if (pb._obsvarName2Ind.containsKey(leSupport.get(0))
							&& pb._varName2Ind.containsKey(leSupport.get(1))) {
						var0 = pb.obsvarName2obsvar.get(leSupport.get(0));
						var1 = pb.varName2var.get(leSupport.get(1));
						if (i == 0) {
							obs2correspondingStateVarName[j] = leSupport.get(1);
						} else {
							obsIdenticalToState[j] = obsIdenticalToState[j]
									&& (obs2correspondingStateVarName[j] == leSupport
											.get(1));
						}
					} else if (pb._obsvarName2Ind.containsKey(leSupport.get(1))
							&& pb._varName2Ind.containsKey(leSupport.get(0))) {
						var0 = pb.obsvarName2obsvar.get(leSupport.get(1));
						var1 = pb.varName2var.get(leSupport.get(0));
						if (i == 0) {
							obs2correspondingStateVarName[j] = leSupport.get(0);
						} else {
							obsIdenticalToState[j] = obsIdenticalToState[j]
									&& (obs2correspondingStateVarName[j] == leSupport
											.get(0));
						}
					} else {
						obsIdenticalToState[j] = false;
					}

					JDDNode egal = JDD.VariablesEquals(var0, var1);

					obsIdenticalToState[j] = obsIdenticalToState[j]
							&& JDD.EqualSupNorm(egal,
									ADDobsFunc.put(theCouple, res), 0);

					JDD.Deref(egal);
				} else {
					obsIdenticalToState[j] = false;
				}
			}
		}

		int NBvisibleStates = 0;
		for (int j = 0; j < pb.nObsVars; j++) {
			if (obsIdenticalToState[j]) {
				NBvisibleStates++;
			}
		}
		String[] visibleStates = new String[NBvisibleStates];

		int leCPT = 0;

		System.out.println("\n==========================================\n");
		System.out.println("VISIBLE STATE VARIABLES: ");
		for (int j = 0; j < pb.nObsVars; j++) {
			if (obsIdenticalToState[j]) {
				visibleStates[leCPT] = obs2correspondingStateVarName[j];
				System.out.print(visibleStates[leCPT] + " ");
				leCPT++;
			}

		}
		System.out.println("\n==========================================\n");

		System.out.println("HIDDEN STATE VARIABLES: ");
		int NBhiddenStates = pb.nStateVars - NBvisibleStates;
		String[] hiddenStates = new String[NBhiddenStates];
		int leCPTh = 0;
		for (int i = 0; i < pb.nStateVars; i++) {
			Boolean VS = true;
			String primedOne = pb._ind2VarName.get(i) + "'";
			for (int j = 0; j < NBvisibleStates; j++) {
				VS = VS && !visibleStates[j].equals(primedOne);
			}
			if (VS) {
				hiddenStates[leCPTh] = primedOne;
				System.out.print(hiddenStates[leCPTh] + " ");
				leCPTh++;
			}
		}

		double nbVC = Math.pow(2, hiddenStates.length);
		double nbVV = Math.pow(2, visibleStates.length);
		// double nbVCd = (double) nbVC;

		int LL = qualScale.size();
		double LLd = (double) qualScale.size();
		if (nbVV == 0)
			nbVV++;
		double nbB = (Math.pow(LLd, nbVC) - Math.pow(LLd - 1, nbVC)) * nbVV;

		double loga = Math.log(nbB) / Math.log(2);

		System.out.println("\n==========================================\n");

		System.out.println("scale size: " + LL);
		System.out.println("size of the belief space: " + nbB);
		System.out.println("number of belief variables: " + loga);

	}

	// deref of all data of the problem
	public void derefPOPPUDD() {
		// DEREF TRANSITION/OBSERVATION FUNCTIONs
		System.out.println(" \n \n DEREF ADDs");
		for (int i = 0; i < pb.actNames.size(); i++) {
			System.out.println("\nfunctions for action " + pb.actNames.get(i));
			System.out.println("\ntrans:");
			for (int j = 0; j < pb.nStateVars; j++) {
				String variable = pb.varNames.get(j);
				String action_name = pb.actNames.get(i);
				Pair<String, String> varAction = new Pair<String, String>(
						variable, action_name);

				JDD.Deref(ADDtransFunc.get(varAction));
				DebugJDD.REFCOUNT(ADDtransFunc.get(varAction));
			}
			System.out.println("\nobs:");
			for (int j = 0; j < pb.nObsVars; j++) {
				String variable = pb.obsvarNames.get(j);
				String action_name = pb.actNames.get(i);
				Pair<String, String> varAction = new Pair<String, String>(
						variable, action_name);
				JDD.Deref(ADDobsFunc.get(varAction));
				DebugJDD.REFCOUNT(ADDobsFunc.get(varAction));
			}
			System.out.println("\npref:");
			JDD.Deref(this.action2pref.get(pb.actNames.get(i)));
			DebugJDD.REFCOUNT(this.action2pref.get(pb.actNames.get(i)));
		}

		for (String dd_name : pb.existingDds.keySet()) {
			JDD.Deref(pb.existingDds.get(dd_name));
		}

		JDD.Deref(pb.discount);
		JDD.Deref(pb.horizon);

		// DEREF VARIABLES
		System.out.println(" \n \n DEREF VARS");
		for (int i = 0; i < pb.varNames.size(); i++) {
			System.out.print(i + ": ");
			DebugJDD.REFCOUNT(pb.varsCUDD.getVar(i));
			System.out.print(" -- ");
		}
		System.out.println("");
		pb.varsCUDD.derefAll();
		System.out.println("");
		for (int i = 0; i < pb.varNames.size(); i++) {
			System.out.print(i + ": ");
			DebugJDD.REFCOUNT(pb.varsCUDD.getVar(i));
			System.out.print(" -- ");
		}
		pb.obsvarCUDD.derefAll();
		JDD.Deref(ZEROD);
		JDD.Deref(ONED);
		// DEREF ONED ZEROD
		DebugJDD.REFCOUNT(ZEROD);
		System.out.println("");
		DebugJDD.REFCOUNT(ONED);
		JDD.Deref(pb.init);
	}

	
	public Boolean isInADD(JDDNode aTree, JDDNode treeCollector, JDDVars collectorVars)
	{
		JDD.Ref(aTree); 
		JDD.Ref(treeCollector); // c'est l'ADD_beta de l'itération précédente
		JDDNode difference = JDD.Apply(JDD.MINUS, aTree, treeCollector); // diff = belief - ADD_beta // ET LA
		JDD.Ref(difference);
		JDDNode isPos = JDD.GreaterThanEquals(difference, 0.0d); // isPos =  1_{diff>=0}
		JDD.Ref(difference);
		JDDNode isStrictNeg = JDD.LessThan(difference, 0.0d); // isStrictNeg = 1_{diff<0}
		JDD.Ref(difference);
		JDDNode temp = JDD.Apply(JDD.TIMES, difference, isStrictNeg); // diff*1_{diff<0}
		difference = JDD.Apply(JDD.TIMES, difference, isPos); // diff*1_{diff>=0}
		difference = JDD.Apply(JDD.MINUS, difference, temp); // abs(diff) // MINUS!!!!!!
		difference = JDD.MaxAbstract(difference, pb.vars);
		JDDNode resultat = JDD.MinAbstract(difference, collectorVars); // [difference killed]
		Boolean leResultat = false; 
		if ( (resultat.isConstant()) && (resultat.getValue() == 0) ) {
				leResultat = true; // if abs(diff)=0, then the belief was already in ADD_beta
		}
		JDD.Deref(resultat);
		return leResultat;
	}
	
	// returns the ADD encoding 0-1 function which returns 1 for belief variables instantiation used by treeCollector to store aTree 
	public JDDNode whichBelief(JDDNode aTree, JDDNode treeCollector) //, JDDVars collectorVars)
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
		difference = JDD.MaxAbstract(difference, pb.vars);
		/*
		System.out.println("\n\n========================\nisInADD: difference");
		afficheDdNodeMDP(difference, 0, 't');
		System.out.println("\n========================\n");
		*/
		JDDNode resultat = JDD.LessThanEquals(difference, 0d); // [difference killed]
		
		return resultat;
	}

	// forward search to get all reachable beliefs
	public void POPPUDDinit() {
		// TODO correct all TODOs, (times != and, 1- != not, ... )
		// TODO see what we forgot in the original solver (scale belief, test that 1_{b^ao' = b'} is complete..)
		
		Boolean debug = false;		
		double nbObs_d = Math.pow(2, pb.nObsVars);
		int nbObs = (int) nbObs_d; // observations amount
		
		// creation of the first belief variable
		long beliefSpaceSize = 1; // amount of reached beliefs 
		int lastVarIndex = 2 * pb.nStateVars + 2 * pb.nObsVars; // variables amount
		int nbBeliefVars = 1; // (boolean) belief var amount
		// current bel vars container
		JDDNode beliefVARIABLE = JDD.Var(lastVarIndex); 
		 // belief variables
		BeliefVars.addVar(beliefVARIABLE); // added to both variables containers (beliefs one & all one)
		// previous bel vars container
		JDDNode beliefVARIABLEP = JDD.Var(lastVarIndex+1);
		PrimedBeliefVars.addVar(beliefVARIABLEP);
		// variables used to select belief in the current beta_SPOP ADD (which does not change except at the end of the while loop, 
		//                                                                                    while ADD_beta and BeliefVars do change)
		JDDVars previousBeliefVars = new JDDVars();
		JDDVars previousPrimedBeliefVars = new JDDVars();
		previousBeliefVars.addVar(beliefVARIABLE);
		previousPrimedBeliefVars.addVar(beliefVARIABLEP);
		int prevNbBeliefVars = 1; 
		// Only used at end: previousBeliefVars has to contain 
		// new belief variables and contains already old ones.
		// This integer help us to add only new variables
		
		// at each iteration lastBeliefIndices contains all belief indices 
		// for which beliefs were generated during the previous iteration
		// and BeliefIndices is filled with new beliefs indices
		Vector<Long> lastBeliefIndices = new Vector<Long>();
		lastBeliefIndices.addElement(1l); 
		// first belief has indices 1 (because at the true arrow of the first variable)
		// will contain all indices of belief generated during the current iteration
		Vector<Long> BeliefIndices = new Vector<Long>(); 
		
		// ADD_beta creation
		JDD.Ref(beliefVARIABLE); 
		JDD.Ref(pb.init);
		ADD_beta = JDD.Apply(JDD.TIMES, beliefVARIABLE, pb.init); 
		// we add the first belief which is the initial state
		//used during ADD_beta filling (incremented when adding a new belief, 
		// and zero when a new variable is created)
		long cptPlace = 0; 
		// WARNING: no automated possibilistic normalization (feed the algorithm with a possibilistic belief)
		
		// has to be used during computation over beliefs
		JDDNode indicPrevBeliefs = JDD.Constant(0d);
		JDDNode indicCurrentBeliefs = beliefVARIABLE;
		JDD.Ref(indicCurrentBeliefs);
		
		// initialization of the strategy
		this.delta = beliefVARIABLE;
		JDD.Ref(this.delta);

		// infinite horizon
		int cpt = 1;
	
		// following hashtables store observation functions (trees) and belief transition indicators (trees)
		JDDNode ADDZERO = JDD.Constant(0d);
		for (int act=0;act<pb.actNames.size();act++)
		{
			JDD.Ref(ADDZERO);
			pi_OP_BelA.put(act,ADDZERO); // belief variables used to stok beliefs from these OP are added to the tree at the end of the while iteration
			JDD.Ref(ADDZERO);
			ind_BAOPBP.put(act,ADDZERO); // as for ADD_beta, the update of ind_BAOPBP is done during iterations, 
										// the add of variables also.
		}
		JDD.Deref(ADDZERO);
		mu = JDD.Constant(0d);
		
		//while (cpt < H) { // instead cpt < horizon:  while ∀β, ∀a∈A, ∀o∈O (such that π(o|β,a)!=0) there exists β' such that 1_{β^{a,o} = β'} = 1.
		JDDNode indicDiff = JDD.Constant(1d);
		while (!(indicDiff.isConstant() && indicDiff.getValue()==0d))
		{
			
			System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!\niteration: " + cpt + "\n!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			JDDNode ADD_beta_lastIteration = ADD_beta; // ADD_beta will be filled with beliefs during iterations of actions' loop:
			JDD.Ref(ADD_beta_lastIteration); // ADD_beta_lastIteration, constant during the while iteration, will be used at each action iteration to generate next beliefs
			
			// indic new beliefs
			JDD.Ref(indicPrevBeliefs);
			JDD.Ref(indicCurrentBeliefs);
			JDD.Deref(indicDiff);
			indicDiff = JDD.Apply(JDD.MINUS, indicCurrentBeliefs, indicPrevBeliefs); 
			
			// preference computation (value function horizon 0 and associated action)
			
			// TODO TEST with the involution (instead of 1-...)
			// Collections.sort(qualScale);
			// System.out.println("qualScale: " + qualScale.toString());
			// JDDNode mubetap_OP = pb.involution2(betap_SPOP, qualScale, allVars); 

			// TODO!! calcul de mu et de la politique à horizon 0 argmax_a min_s max (mu^a,nonb) )
			JDD.Ref(ADD_beta);
			JDD.Ref(indicDiff);
			JDDNode necessityCompl = JDD.Apply(JDD.TIMES, indicDiff, ADD_beta); 
			JDD.Ref(indicDiff);
			necessityCompl = JDD.Apply(JDD.MINUS, indicDiff, necessityCompl);			// 1-b // TODO inversion d'échelle
			
			JDDNode muNewPart = JDD.Constant(0d);
			for (int action = 0; action < pb.actNames.size(); action++) 
			{
				JDD.Ref(indicDiff);
				JDD.Ref(action2pref.get(pb.actNames.get(action)));
				JDDNode prefLimitedToNewBeliefs = JDD.Apply(JDD.TIMES, action2pref.get(pb.actNames.get(action)), indicDiff);
				
				JDD.Ref(necessityCompl);
				JDDNode q0a = JDD.Apply(JDD.MAX, prefLimitedToNewBeliefs, necessityCompl);
				q0a = JDD.MinAbstract(q0a, pb.vars);
				// q0a est nul en dehors de diff car  prefLimitedToNewBeliefs et necessityCompl le sont
				
				JDD.Ref(q0a);
				JDD.Ref(muNewPart);
				JDDNode diffQaMu = JDD.Apply(JDD.MINUS, q0a, muNewPart); // muNewPart est nul en dehors de diff car q0a l'est, et car il était initialement nul
				JDDNode indicBeliefWhereActionMaximizes = JDD.GreaterThan(diffQaMu, 0.0d); // ainsi indicBeliefWhereActionMaximizes est nul en dehors de diff
				JDD.Ref(indicBeliefWhereActionMaximizes);
				JDDNode nonIBWAM = JDD.Apply(JDD.MINUS, JDD.Constant(1d), indicBeliefWhereActionMaximizes); // nonIBWAM contient le complementaire de diff
				this.delta = JDD.Apply(JDD.TIMES, nonIBWAM, this.delta); // mais c'est là que delta reste identique ( donc zero la où il n'y a pas de belief, 
																			// et la politique calculée précédemment sinon
				indicBeliefWhereActionMaximizes = JDD.Apply(JDD.TIMES, JDD.Constant(action+1), indicBeliefWhereActionMaximizes);
				this.delta =  JDD.Apply(JDD.PLUS, this.delta, indicBeliefWhereActionMaximizes);
				
				muNewPart = JDD.Apply(JDD.MAX, muNewPart, q0a);
			}
			JDD.Deref(necessityCompl);
			// mu got new variables at the end of the while loop
			mu = JDD.Apply(JDD.PLUS, mu, muNewPart);

			
			// UPDATE OF what is prev/current/valued beliefs for current set of variables
			JDDNode valuedBeliefs = indicPrevBeliefs;
			//JDD.Deref(indicPrevBeliefs);
			indicPrevBeliefs = indicCurrentBeliefs;
			JDD.Ref(indicPrevBeliefs);
			JDDNode trueMan = JDD.Constant(1d);
			// loop over actions
			for (int action = 0; action < pb.actNames.size(); action++) {
				String MONaction = pb.actNames.get(action);
				System.out.println("action: " + MONaction);
				// ADD_beta's beliefs update:
				//> distribution over next states
				// pi_SP_beta = pi(s'|beta,a) = max_s min[ t(s'|s,a) , beta(s) ]
				
				// possibility distribution over S computation
				JDD.Ref(indicDiff); 
				JDD.Ref(ADD_beta_lastIteration); 
				JDDNode pi_SP_beta = JDD.Apply(JDD.TIMES, ADD_beta_lastIteration, indicDiff); // only over belief stored in last iteration
				for (int stateVar = 0; stateVar < pb.nStateVars; stateVar++) { // TRANS PART  
					String variable = pb.varNames.get(stateVar);
					Pair<String, String> theCouple = new Pair<String, String>(variable, MONaction);
					JDD.Ref(ADDtransFunc.get(theCouple));
					pi_SP_beta = JDD.Apply(JDD.MIN, pi_SP_beta,	ADDtransFunc.get(theCouple));
				}
				pi_SP_beta = JDD.MaxAbstract(pi_SP_beta, pb.vars); // max_s
				
				// possibility distribution over O computation

				//> joint distribution
				// pi_SPOP_beta = pi(s', o'|beta,a) = min[ o(o'|s',a) , pi(s'|beta,a) ]
				JDDNode pi_SPOP_beta = pi_SP_beta; // pi_SP_beta no longer used  
				for (int obsVar = 0; obsVar < pb.nObsVars; obsVar++) { // OBS PART
					String variable = pb.obsvarNames.get(obsVar);
					Pair<String, String> theCouple = new Pair<String, String>(variable, MONaction);
					JDD.Ref(ADDobsFunc.get(theCouple));
					pi_SPOP_beta = JDD.Apply(JDD.MIN, pi_SPOP_beta, ADDobsFunc.get(theCouple));
				}
				
				//> distribution over next observation
				// MAX_SP = max_s' pi(s', o'|beta,a) 
				JDD.Ref(pi_SPOP_beta);
				JDDNode MAX_SP = JDD.MaxAbstract(pi_SPOP_beta, pb.primedVars); //max_s' = pi(o'|beta,a)
				
				// UPDATE next observation distribution based on current belief
				// as the computation of MAX_SP is based on indicDiff*ADD_beta_lastIteration, 
				// MAX_SP is 0 for beliefs in previous indicPrevBeliefs (valuedBeliefs)
				JDD.Ref(MAX_SP);
				JDDNode newTrans = JDD.Apply(JDD.PLUS, pi_OP_BelA.get(action), MAX_SP); //  [pi_OP_BelA.get(action) killed]
				pi_OP_BelA.put(action,newTrans);
				//System.out.println("printsupport action " + pb.actNames.get(action));
				//JDD.PrintSupport(pi_OP_BelA.put(action,newTrans));
				
				// we now compute betap_SPOP, an (ADD) function which return the next belief for each possible observation
				// indicate, for each o' and beta, where s' maximizes
				JDD.Ref(MAX_SP);
				JDD.Ref(pi_SPOP_beta);
				JDDNode diffFctMaxFct = JDD.Apply(JDD.MINUS, pi_SPOP_beta, MAX_SP);
				JDDNode indicatriceArgMax = JDD.GreaterThanEquals(diffFctMaxFct, 0.0);
				
				// if we use directly indicatriceArgMax, for impossible observations o_imp indicatriceArgMax =1 for each (o_imp, s')
				// (it contains the case belief=0, i.e. belief not stored)
				// indicates for each previous belief, impossible observations
				JDDNode impossibleObs = JDD.LessThanEquals(MAX_SP, 0.0); // [MAX_SP killed]
				
				// belief will be set to 0 where pi(o'|beta,a)=0 (impossible belief because observation impossible)
				// TODO: ici, à la place, on peut faire JDDNode possibleObs = JJDD.GreaterThan(MAX_SP,0.0)
				// et indicatriceArgMax = JDD.And(indicatriceArgMax, possibleObs);
				JDD.Ref(impossibleObs);
				indicatriceArgMax = JDD.Apply(JDD.MINUS, indicatriceArgMax,impossibleObs);   
				indicatriceArgMax = JDD.GreaterThan(indicatriceArgMax, 0.0); 

				// first step
				JDDNode betap_SPOP = indicatriceArgMax; // the new belief beta (which depends on o') equals 1 where s' maximizes (suite ensuite)
				JDD.Ref(betap_SPOP);

				JDDNode NONindicatriceArgMax = JDD.Apply(JDD.MINUS, JDD.Constant(1d), indicatriceArgMax); // [indicatriceArgMax killed] // TODO replace "1-" and use "NOT"
				
				NONindicatriceArgMax = JDD.Apply(JDD.MINUS, NONindicatriceArgMax,impossibleObs); // [impossibleObs killed] // TODO use NOR instead of (jacques-jean)>=0
				NONindicatriceArgMax = JDD.GreaterThan(NONindicatriceArgMax, 0.0);
				
				// second step
				//JDD.Ref(pi_SPOP_beta);
				NONindicatriceArgMax = JDD.Apply(JDD.TIMES, NONindicatriceArgMax, pi_SPOP_beta); // and joint distribution otherwise 
				// belief for each observation (and zero where observation is impossible) 
				betap_SPOP = JDD.Apply(JDD.PLUS, betap_SPOP, NONindicatriceArgMax); // [NONindicatriceArgMax killed]
				JDD.ReduceHeap(1,1);
				// betap_SPOP is built from the constant ADD_beta_lastIteration for each action during iteration
				
				// method with lastBeliefIndices and BeliefIndices is used to test only beliefs FROM BELIEFS generated during last (while) iteration
				// (beliefs from beliefs already stocked before last iteration have been surely added by previous iteration)
		
				for (int indice = 0; indice < lastBeliefIndices.size(); indice++) // only for just previously (last iteration) generated beliefs
				{	// INDIQUEBEL (using vars of previous iteration)
					long bel = lastBeliefIndices.get(indice); // not for all previous belief 
					JDDNode indiqueBel = JDD.Create();
					indiqueBel = JDD.SetVectorElement(indiqueBel, previousBeliefVars, bel, 1d); // next beliefs enumeration (w.r.t belief vars) 
					// previousBeliefVars contains variables concerning ADD_beta_last_iteration
					
					for (int obs = 0; obs < nbObs; obs++) // for each observation (#O=2^{#obs. vars}) 
					{ 
						JDDNode indiqueObs = JDD.Create();
						indiqueObs = JDD.SetVectorElement(indiqueObs, pb.primedObsvar, obs, 1d); // next beliefs enumeration (w.r.t observation variables)  
						
						// indicate a new belief for a given obs (indiqueObs) and a given previous belief (indiqueBel)
						JDD.Ref(indiqueBel);
						indiqueObs = JDD.Apply(JDD.TIMES, indiqueBel, indiqueObs);
					
						JDD.Ref(betap_SPOP); // betap_SPOP is the ADD which represents next beta for each obs'  and each assigment of beta variables
						
						JDD.Ref(indiqueObs);
						JDDNode firstNewBelief = JDD.Apply(JDD.TIMES, betap_SPOP, indiqueObs); // indiqueObs = indiqueOb * indiqueBel TODO AND operator instead!!
					
						// unnecessary variables are cleaned
						firstNewBelief = JDD.MaxAbstract(firstNewBelief, pb.primedObsvar); // here primed obs vars are not in the tree anymore
						firstNewBelief = JDD.MaxAbstract(firstNewBelief, BeliefVars); // here belief vars are not in the tree anymore
						firstNewBelief = JDD.SwapVariables(firstNewBelief, pb.primedVars, pb.vars); // s' -> s
					
						if (debug)
							afficheDdNodeMDP(firstNewBelief, 0, 't');
						
						Boolean nonNullBelief = true;
						if (firstNewBelief.isConstant() && firstNewBelief.getValue() == 0)
							nonNullBelief = false;
						
						if (nonNullBelief) // if belief !=0
						{
							// We test if firstNewBelief is already in the tree: if not the belief is added. 
							Boolean alreadyInTree =true; //isInADD(firstNewBelief, ADD_beta, BeliefVars);
							
							JDDNode res = whichBelief(firstNewBelief, ADD_beta); //, BeliefVars);
							if (res.isConstant() && res.getValue()==0d)
							{
								alreadyInTree = false;
							}
							
							if (!alreadyInTree) // if the belief does not already exist in ADD_beta
							{		
								JDD.Deref(res);
								beliefSpaceSize++; // !SIZE OF THE BELIEF SPACE IS INCREMENTED! 
												   // NEED FOR AN OTHER VARIABLE OR NOT? (next if/then decides)
								System.out.println("belief num " + beliefSpaceSize);
								double currentNbBeliefVarsD = (double) nbBeliefVars; // current amount of belief VARIABLES 
								double currentTotBel = Math.pow(2.0,currentNbBeliefVarsD);
								long currentTotBelL = (long) currentTotBel;
								
								if ( beliefSpaceSize > currentTotBelL ) // NEED FOR AN OTHER BELIEF VARIABLE
								{ 
									// update indices of new beliefs (for this iteration)
									for (int i=0;i<BeliefIndices.size();i++)
									{
										BeliefIndices.setElementAt(BeliefIndices.get(i)*2+1, i); // indicateurs des beliefs dans ADD_beta
									}  // notons que lastBeliefIndices ne prend la valeur de BeliefIndices qu'à la fin du while
									// ces indices ne serviront qu'à l'itération suivante
									// pour cette iteration, on se contente de lastBeliefIndices et de ADD_beta_last_iteration
									BeliefIndices.add(0l); // new belief's indice
									
									// INDICBELIEF (using just previous vars)
									JDDNode indicBelief = JDD.Create();
									indicBelief = JDD.SetVectorElement(indicBelief, BeliefVars, 0, 1d); // sur la base des (just) previous variables (toutes fausses)
									JDD.Ref(firstNewBelief);
									JDD.Ref(indicBelief); 
									JDDNode newPartTree = JDD.Apply(JDD.TIMES, indicBelief, firstNewBelief); // nouveau belief, rataché à toutes 
																											// les (just) previous variables, fausses
									
									// new bel var creation
									System.out.println("!!!NEW VARIABLE CREATED!!!");
									JDDNode newBeliefVARIABLE = JDD.Var(lastVarIndex + 2*nbBeliefVars);
									BeliefVars.addVar(newBeliefVARIABLE);
									JDDNode newBeliefVARIABLE2 = JDD.Var(lastVarIndex + 2*nbBeliefVars +1);
									PrimedBeliefVars.addVar(newBeliefVARIABLE2);									
									nbBeliefVars++;
									JDD.Ref(newBeliefVARIABLE);
									trueMan = JDD.Apply(JDD.TIMES, trueMan, newBeliefVARIABLE);

									// ADD_beta
									JDD.Ref(newBeliefVARIABLE);
									ADD_beta = JDD.ITE(newBeliefVARIABLE, ADD_beta, newPartTree); // [newPartTree killed] 
																									// le nouveau belief est rataché à TOUTES les vars, fausses
									cptPlace=1;
									
									// current belief mask update (indicCurrentBeliefs)
									JDD.Ref(newBeliefVARIABLE); 
									JDD.Ref(indicBelief); // sur la base des (just) previous variables (toutes fausses)
									indicCurrentBeliefs = JDD.ITE(newBeliefVARIABLE, indicCurrentBeliefs, indicBelief); 
									
									
									
									// first part of ind_BAOPBP.get(action)
									JDD.Ref(newBeliefVARIABLE);
									JDD.Ref(newBeliefVARIABLE2);
									JDDNode allTrue = JDD.Apply(JDD.TIMES, newBeliefVARIABLE, newBeliefVARIABLE2);
									JDDNode firstPartInd = JDD.Apply(JDD.TIMES, allTrue, ind_BAOPBP.get(action));
									
									// add the new variable to indicBelief, and primes its variables
									indicBelief = JDD.SwapVariables(indicBelief, BeliefVars, PrimedBeliefVars);
									JDD.Ref(newBeliefVARIABLE2);
									JDD.Ref(ONED); // TODO plutot JDD.ITE(newBeliefVARIABLE2,JDD.Constant(0d),indicBelief);
									JDDNode NONnbv = JDD.Apply(JDD.MINUS, ONED, newBeliefVARIABLE2); 
									indicBelief = JDD.Apply(JDD.TIMES, indicBelief, NONnbv); // all false
									// TODO instead??:
									// JDDNode indicPrimedBeliefFull = JDD.Create();
									// indicPrimedBeliefFull = JDD.SetVectorElement(indicPrimedBeliefFull, PrimedBeliefVars, 0, 1d);
									
									JDD.Ref(trueMan); // trueMan is the ADD which gives 1 if and only if all variables are true
									indiqueObs = JDD.Apply(JDD.TIMES, indiqueObs, trueMan);	// we add all variables which appear during 
																							// the current iteration (horizon i.e. while iteration)
									
									JDD.Ref(indiqueObs);
									JDDNode temporaireADD = JDD.Apply(JDD.TIMES, indiqueObs, indicBelief); // [indicBelief killed] // TODO use indicPrimedBeliefFull
									temporaireADD = JDD.Apply(JDD.PLUS, firstPartInd, temporaireADD);
									ind_BAOPBP.put(action, temporaireADD);
									
									// add new variables to 1_{beta^{a,o'}=beta'} for other actions
									for (int a=0;a<pb.actNames.size();a++)
									{
										if (a!=action) // TODO on aurait pu faire cette boucle au moment de la mise à jour de l'arbre  concernant l'action courante:
										{					// il aurait suffit ensuite de faire ind_BAOPBP.get(action) = ind_BAOPBP.get(action) + temporaireADD
											JDD.Ref(newBeliefVARIABLE);     // au lieu de passer par firstPartInt
											JDD.Ref(newBeliefVARIABLE2);
											JDDNode temporaireADD2 = JDD.Apply(JDD.TIMES, newBeliefVARIABLE, newBeliefVARIABLE2);
											temporaireADD2 = JDD.Apply(JDD.TIMES, ind_BAOPBP.get(a), temporaireADD2); // TODO instead and!
											ind_BAOPBP.put(a, temporaireADD2);
										}
									}
									
								} 
								else // NO NEW VARIABLE NEEDED
								{
									JDDNode indicBelief1 = JDD.Create();
									// indicates new belief
									indicBelief1 = JDD.SetVectorElement(indicBelief1, BeliefVars, 2l*cptPlace, 1d); 
									// last belief var is false for new beliefs (that's why "2*")
									BeliefIndices.add(2l*cptPlace);
									cptPlace++;
									
									// ADD_beta update
									JDD.Ref(firstNewBelief);
									JDD.Ref(indicBelief1);
									JDDNode newPartTree = JDD.Apply(JDD.TIMES, indicBelief1, firstNewBelief); 
									ADD_beta = JDD.Apply(JDD.PLUS, ADD_beta, newPartTree); // [newPartTree killed]
									
									// current belief mask update (indicCurrentBeliefs)
									JDD.Ref(indicBelief1);
									indicCurrentBeliefs = JDD.Apply(JDD.PLUS, indicCurrentBeliefs, indicBelief1); 
									
									// update of 1_{beta^{a,o'}=beta'} 
									
									indicBelief1 = JDD.SwapVariables(indicBelief1, BeliefVars, PrimedBeliefVars);
									
									// trueMan is the ADD which gives 1 if and only if all variables are true
									// we add all variables which appear during 
									// the current iteration (horizon i.e. while iteration)
									JDD.Ref(trueMan); 
									indiqueObs = JDD.Apply(JDD.TIMES, indiqueObs, trueMan);
									JDD.Ref(indiqueObs);
									JDDNode temporaireADD = JDD.Apply(JDD.TIMES, indiqueObs, indicBelief1); // [indicBelief1 killed]
									temporaireADD = JDD.Apply(JDD.PLUS, ind_BAOPBP.get(action), temporaireADD);
									ind_BAOPBP.put(action, temporaireADD);
								}
								
							} 
							else 
							{
								if (debug)	
								{
									System.out.println("NEW BELIEF ALREADY EXISTS");
								}
								// res indicates which (already stored) belief has just been reached
								res = JDD.SwapVariables(res, BeliefVars, PrimedBeliefVars);
								// indiqueObs is indiqueObs*indiqueBelief
								// trueMan is the ADD which gives 1 if and only if all variables are true
								// we add all variables which appear during 
								// the current iteration (horizon i.e. while iteration)
								JDD.Ref(trueMan);
								indiqueObs = JDD.Apply(JDD.TIMES, indiqueObs, trueMan);
								JDD.Ref(indiqueObs);
								JDDNode temporaireADD = JDD.Apply(JDD.TIMES, indiqueObs, res); // [res killed]
								temporaireADD = JDD.Apply(JDD.PLUS, ind_BAOPBP.get(action), temporaireADD);
								ind_BAOPBP.put(action, temporaireADD);
								//JDD.Deref(res);
							}
							// test if the current belief is now in ADD_beta
							Boolean leBooleen = isInADD(firstNewBelief,ADD_beta,BeliefVars);
							if(leBooleen)
							{
								//System.out.println("maintenant en tout cas, il existe\n\n");
							}
							else
							{
								afficheDdNodeMDP(firstNewBelief, 0, 't');
								System.err.println("!! Problem: The current belief was not put in ADD_beta !!1");
								System.exit(0);
							}
							JDD.Deref(firstNewBelief);
						} 
						else 
						{
							// impossible belief
							JDD.Deref(firstNewBelief);
						}
						JDD.Deref(indiqueObs);
					}//<boucle obs
					JDD.Deref(indiqueBel);
				} //<boucle bel
				JDD.Deref(betap_SPOP);
			}//<boucle sur les actions
			
			JDD.Deref(ADD_beta_lastIteration);
			//prevBeliefSpaceSize = prevBeliefSpaceSize2;
			cpt++;
			System.out.println("lastBeliefIndices: " + lastBeliefIndices);
			System.out.println("BeliefIndices: " + BeliefIndices);
			lastBeliefIndices.removeAllElements();
			lastBeliefIndices.addAll(BeliefIndices);
			BeliefIndices.removeAllElements();
			
			// ad variables to functions
			for (int bvar=prevNbBeliefVars; bvar<nbBeliefVars;bvar++)
			{
				// add the new variables
				previousBeliefVars.addVar(BeliefVars.getVar(bvar));
				previousPrimedBeliefVars.addVar(PrimedBeliefVars.getVar(bvar));
				
				// we add new variables to pi_OP_BelA
				for (int act=0;act<pb.actNames.size();act++)
				{
					JDD.Ref(BeliefVars.getVar(bvar));
					JDDNode ADDtemp = JDD.Apply(JDD.TIMES, pi_OP_BelA.get(act), BeliefVars.getVar(bvar));
					pi_OP_BelA.put(act,ADDtemp);
				}
				JDD.Ref(BeliefVars.getVar(bvar));
				mu = JDD.Apply(JDD.TIMES, BeliefVars.getVar(bvar), mu);
				JDD.Ref(BeliefVars.getVar(bvar));
				indicPrevBeliefs = JDD.Apply(JDD.TIMES, BeliefVars.getVar(bvar), indicPrevBeliefs);
				JDD.Ref(BeliefVars.getVar(bvar));
				this.delta = JDD.Apply(JDD.TIMES, BeliefVars.getVar(bvar), this.delta);
			}
			prevNbBeliefVars = nbBeliefVars;
			JDD.Deref(valuedBeliefs); // TODO NSAR
			
			JDD.Ref(indicCurrentBeliefs);
			JDD.Ref(indicPrevBeliefs);
			JDDNode ladiff = JDD.Apply(JDD.MINUS, indicCurrentBeliefs, indicPrevBeliefs);
			this.delta = JDD.Apply(JDD.PLUS, this.delta, ladiff);
			
			JDD.Deref(trueMan);
		}//< while loop
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		System.out.println("NB BELIEFS: " + beliefSpaceSize);
		//JDD.Deref(mu);
		System.out.println("state VARIABLES:");
		System.out.println(pb.vars);
		System.out.println("primed state VARIABLES:");
		System.out.println(pb.primedVars);
		System.out.println("obs VARIABLES: (not used!!)");
		System.out.println(pb.obsvar);
		System.out.println("primed obs VARIABLES:");
		System.out.println(pb.primedObsvar);
		System.out.println("belief VARIABLES:");
		System.out.println(BeliefVars);
		System.out.println("primed belief VARIABLES:");
		System.out.println(PrimedBeliefVars);
		
		JDD.Deref(indicDiff);
		JDD.Deref(indicPrevBeliefs);
		beliefsMask = indicCurrentBeliefs;
	}
	
	
	// PPUDD for POMDPs
	public void POPPUDD() {
		JDDNode uc = mu;
		JDDNode ustar = JDD.Constant(0d);
		JDD.Ref(uc);
		JDD.Ref(ustar);
		JDDNode theDiff = JDD.Apply(JDD.MINUS, uc, ustar);
		int iterationCounter = 1;
		while (!(theDiff.isConstant() && theDiff.getValue()==0d))
		{
			JDD.Deref(ustar);
			ustar = uc;
			JDD.Ref(ustar);
			System.out.println("while POPPUDD:" + iterationCounter);
			for (int action = 0; action < pb.actNames.size(); action++) 
			{
				//System.out.println("action: " + action);
				JDD.Ref(ustar);
				JDDNode qa = JDD.SwapVariables(ustar, BeliefVars, PrimedBeliefVars);
				JDD.Ref(ind_BAOPBP.get(action));
				qa = JDD.Apply(JDD.MIN, qa, ind_BAOPBP.get(action));
				qa = JDD.MaxAbstract(qa, PrimedBeliefVars);
				JDD.Ref(pi_OP_BelA.get(action));
				qa = JDD.Apply(JDD.MIN, qa, pi_OP_BelA.get(action));
				qa = JDD.MaxAbstract(qa, pb.primedObsvar);
				JDD.Ref(qa);
				uc = JDD.Apply(JDD.MAX, uc, qa);
				
				// policy
				JDD.Ref(uc);
				JDDNode QAminusUC = JDD.Apply(JDD.MINUS, qa, uc); // [qa killed]
				JDDNode indicQAgtUC = JDD.GreaterThanEquals(QAminusUC, 0.0d);
				
				JDD.Ref(uc);
				JDD.Ref(ustar);
				JDDNode UCminusUSTAR = JDD.Apply(JDD.MINUS, uc, ustar);
				JDDNode indicUCsgtUSTAR = JDD.GreaterThan(UCminusUSTAR, 0.0d);
				
				JDDNode indicUpdate = JDD.Apply(JDD.TIMES, indicQAgtUC, indicUCsgtUSTAR); // [args are killed] // TODO plutot "and"
				JDD.Ref(beliefsMask);
				indicUpdate = JDD.Apply(JDD.TIMES, beliefsMask, indicUpdate); // TODO plutot and
				
				JDD.Ref(indicUpdate);
				JDDNode NONindicUpdate = JDD.Apply(JDD.MINUS, JDD.Constant(1d), indicUpdate); // TODO plutot JDD.Not
				JDD.Ref(beliefsMask);
				NONindicUpdate = JDD.Apply(JDD.TIMES, beliefsMask, NONindicUpdate);
				
				indicUpdate = JDD.Apply(JDD.TIMES, indicUpdate, JDD.Constant(action+1));
				this.delta = JDD.Apply(JDD.TIMES, NONindicUpdate, this.delta); // [NONindicUpdate killed]
				this.delta = JDD.Apply(JDD.PLUS, indicUpdate, this.delta); // [indicUpdate killed]
				
				// au premier abord, on peut se dire qu'on peut mettre n'importe quelle valeur de politique pour les variables assignments s.t. 
				// no belief is stored: however, put 0 instead of an other value implies no value is stored -> beneficial in RAM
				// conclusion: on a besoin du dernier indicCurrentBelief = beliefsMask
			}
			JDD.Deref(theDiff);
			JDD.Ref(uc);
			JDD.Ref(ustar);
			theDiff = JDD.Apply(JDD.MINUS, uc, ustar);
			iterationCounter++;
			//afficheDdNodeMDP(theDiff, 0, 't');
		}		
		
		JDD.Deref(theDiff);
		JDD.Deref(uc); // = JDD.Deref(mu)!!
		JDD.Deref(ustar);
		
		System.out.println("policy:");
		afficheDdNodeMDP(this.delta, 0, 't');
	}
}
