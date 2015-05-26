package rddl.parser;

import java.io.FileNotFoundException;


import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.util.*;
//import util.*;

import jdd.*;


// THIS PARSER USES A POSSIBILISTIC TRANSLATION:
// some modifications has to be performed to make it store the purely probabilistic problem.
// It produces possibility distributions, and no probability ones.
public class ParseCUDD implements Serializable
{
	public HashMap<String,JDDNode> existingDds;
	private StreamTokenizer stream;

	public Vector<String> varNames; 
	public Vector<Vector> valNames;
	public Vector<String> actNames;
	public Vector<JDDNode[]> actTransitions;
	public Vector<JDDNode[]> actObserve;
	public Vector<JDDNode> actCosts;
	public JDDNode reward;
	public JDDNode init;
	public JDDNode discount;
	public JDDNode tolerance;
	public JDDNode horizon;
	public boolean unnormalized;
	public int nStateVars;
	public int nObsVars;
	public TreeMap<String,Integer> _varName2Ind; // in CUDD, a variable is represented by an integer
	public TreeMap<Integer,String> _ind2VarName;
	public JDDVars varsCUDD; // all variables: we catch them with varCUDD.getVar(i), i from 0 to 2*nStateVars-1
	public JDDVars vars; // non-primed variables, used to swap, vars.getVar(i) with i from 0 to nStateVars-1
	public TreeMap<String,JDDVars> varName2var; // used to maximize over one variable, varName2var.get(string_name_of_variable)
	public JDDVars primedVars; // primed variables, used to swap, vars.getVar(i) with i from 0 to nStateVars-1
	
	// store observations
	public JDDVars obsvarCUDD;
	public JDDVars obsvar;
	public JDDVars primedObsvar;
	public TreeMap<String, JDDVars> obsvarName2obsvar;
	public Vector<String> obsvarNames; // from 0 to 2*nObsVars-1
	public Vector<Vector> obsvalNames; 
	public TreeMap<String,Integer> _obsvarName2Ind; // in CUDD, a variable is represented by an integer
	public TreeMap<Integer,String> _ind2ObsvarName;
	
	// state and observations variables (non-primed and primed)
	public JDDVars allVariables;

	public ParseCUDD(String fileName)
	{
		existingDds = new HashMap<String,JDDNode>();
		varNames = new Vector<String>();
		valNames = new Vector<Vector>();
		actNames = new Vector<String>();
		actTransitions = new Vector<JDDNode[]>();
		actObserve = new Vector<JDDNode[]>();
		actCosts = new Vector<JDDNode>();
		discount = null;
		tolerance = null;
		horizon = null;
		init = null;
		reward = null;
		unnormalized = false;
		nStateVars = 0;
		nObsVars = 0;
		
		obsvarNames = new Vector<String>();
		obsvalNames = new Vector<Vector>();

		try
		{
			stream = new StreamTokenizer(new FileReader(fileName));
		} catch (FileNotFoundException e)
		{
			System.out.println("Error: file not found\n");
			//System.exit(1);
		}
		stream.wordChars('\'', '\'');
		stream.wordChars('_', '_');
	}

	// TODO this possibilistic translation function is dependent of the fact that 
	//primed variables are the last variables in the trees of the spudd_sperseus files
	public JDDNode possTrad(JDDNode leJDD){
		if (!leJDD.isConstant()) { // it is not a leaf
			int index =  leJDD.getIndex(); // get its index
			JDDNode leElse = leJDD.getElse();
			JDDNode leThen = leJDD.getThen();
			// if child are as well intern nodes, then possTrad we  alors on relance la function sur chacuns
			if (!leElse.isConstant() && !leThen.isConstant()) {
				JDDNode normElse = possTrad(leElse);
				JDDNode normThen = possTrad(leThen);
				JDDNode vart = varsCUDD.getVar(index); // here the index corresponds in the number in VarCUDD: it will no more be the case for observations
				JDD.Ref(vart);
				JDDNode ONED = JDD.Constant(1d);
				JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
				JDD.Ref(vart);
				vart = JDD.Apply(JDD.TIMES, vart, normThen);
				varf = JDD.Apply(JDD.TIMES, varf, normElse);
				JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
				return res;
			} 
			// else,  if both nodes are values (leaves)
			// and if the last variable is primed, 
			// we care about branches L and H now (the max is equal to DD_ONE)
			else if (index>=nStateVars && leElse.isConstant() && leThen.isConstant()) {
				double EE = leElse.getValue();
				double TT = leThen.getValue();
				if (EE > TT) {
					JDDNode vart = varsCUDD.getVar(index);
					JDD.Ref(vart);
					JDDNode ONED = JDD.Constant(1d);
					JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
					JDD.Ref(vart);
					JDDNode JDDTT = JDD.Constant(TT);
					vart = JDD.Apply(JDD.TIMES, vart, JDDTT);
					JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
					return lADD;
				} else if (EE<TT) {
					JDDNode vart = varsCUDD.getVar(index);
					JDDNode JDDEE = JDD.Constant(EE);
					JDD.Ref(vart);
					JDDNode ONED = JDD.Constant(1d);
					JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
					varf = JDD.Apply(JDD.TIMES,JDDEE,varf);
					JDD.Ref(vart);
					JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
					return lADD;
				}
				else
				{
					JDDNode res = JDD.Constant(1d);
					return res;
				}
			}
			else
			{
				System.err.println("Problem in the possibilistic translation in PARSECUDD: the occurrence is not taken into account.");
				return null;
			}
		} else {
			System.out.println("Warning: the ADD is a leaf.");
			JDDNode res = JDD.Constant(1d);
			return res;
		}
	}
	
	

	// TODO another translation
	public JDDNode possTradM2(JDDNode leJDD, int zeroUnDeux){
		// 0 true -> 1
		// 1 false -> 1
		// 2 classical translation
		
		if (!leJDD.isConstant()) { // ce n'est pas une feuille
			int index =  leJDD.getIndex(); // on chope son index
			JDDNode leElse = leJDD.getElse();
			JDDNode leThen = leJDD.getThen();
			// si les enfants sont aussi des noeuds internes, alors on relance la function sur chacuns
			if (!leElse.isConstant() && !leThen.isConstant()) {
				JDDNode normElse = possTrad(leElse);
				JDDNode normThen = possTrad(leThen);
				JDDNode vart = varsCUDD.getVar(index); // here the index corresponds in the number in VarCUDD: it will no more be the case for observations
				JDD.Ref(vart);
				JDDNode ONED = JDD.Constant(1d);
				JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
				JDD.Ref(vart);
				vart = JDD.Apply(JDD.TIMES, vart, normThen);
				varf = JDD.Apply(JDD.TIMES, varf, normElse);
				JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
				return res;
			} 
			else if (index>=nStateVars && leElse.isConstant() && leThen.isConstant()) {
				double EE = leElse.getValue();
				double TT = leThen.getValue();
				if (zeroUnDeux==2)
				{
					if (EE > TT) {
						JDDNode vart = varsCUDD.getVar(index);
						JDD.Ref(vart);
						JDDNode ONED = JDD.Constant(1d);
						JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
						JDD.Ref(vart);
						JDDNode JDDTT = JDD.Constant(TT);
						vart = JDD.Apply(JDD.TIMES, vart, JDDTT);
						JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
						return lADD;
					} else if (EE<TT) {
						JDDNode vart = varsCUDD.getVar(index);
						JDDNode JDDEE = JDD.Constant(EE);
						JDD.Ref(vart);
						JDDNode ONED = JDD.Constant(1d);
						JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
						varf = JDD.Apply(JDD.TIMES,JDDEE,varf);
						JDD.Ref(vart);
						JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
						return lADD;
					}
					else
					{
						JDDNode res = JDD.Constant(1d);
						return res;
					}
				}
				else if (zeroUnDeux==0)
				{
					JDDNode vart = varsCUDD.getVar(index);
					JDDNode JDDEE = JDD.Constant(EE);
					JDD.Ref(vart);
					JDDNode ONED = JDD.Constant(1d);
					JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
					varf = JDD.Apply(JDD.TIMES,JDDEE,varf);
					JDD.Ref(vart);
					JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
					return lADD;
				}
				else if (zeroUnDeux==1)
				{
					JDDNode vart = varsCUDD.getVar(index);
					JDD.Ref(vart);
					JDDNode ONED = JDD.Constant(1d);
					JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
					JDD.Ref(vart);
					JDDNode JDDTT = JDD.Constant(TT);
					vart = JDD.Apply(JDD.TIMES, vart, JDDTT);
					JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
					return lADD;
				}
				else
				{
					System.err.println("PROBLEME DANS TRAD DANS PARSECUDD: 0, 1 ou 2!!");
					return null;
				}
			}
			else
			{
				System.err.println("PROBLEME DANS TRAD DANS PARSECUDD: cas pas pris en compte");
				return null;
			}
		} else {
			System.out.println("ATTENTION: l'ADD EST UNE FEUILLE!");
			JDDNode res = JDD.Constant(1d);
			return res;
		}
	}
	
	
	// TODO involution1
	public JDDNode involution(JDDNode init, Vector<Double> scale){
		if (!init.isConstant()) 
		{ 
			int index =  init.getIndex();
			//System.out.println("L'INDEX: " + _ind2VarName.get(index));
			JDDNode leElse = init.getElse();
			JDDNode leThen = init.getThen();
			JDDNode invElse = involution(leElse,scale);
			JDDNode invThen = involution(leThen,scale);
			JDDNode vart = allVariables.getVar(index);
			JDD.Ref(vart);
			JDDNode ONED = JDD.Constant(1d);
			JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
			JDD.Ref(vart);
			vart = JDD.Apply(JDD.TIMES, vart, invThen);
			varf = JDD.Apply(JDD.TIMES, varf, invElse);
			JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
			return res;
		}	 
		else  // constant
		{
			double valeur = init.getValue();
			int lIndice = scale.indexOf(valeur);
			//System.out.println("lIndice: " + lIndice + " valeur:" + valeur);
			lIndice = scale.size()-lIndice-1;
			return JDD.Constant(scale.get(lIndice));
		}
	}
	
	// TODO involution 2 
	public JDDNode involution2(JDDNode init, Vector<Double> scale, JDDVars VARIABLESsob){
		if (!init.isConstant()) { // not a leaf
			int index =  init.getIndex(); // get its index
			//System.out.println("L'INDEX: " + _ind2VarName.get(index));
			JDDNode leElse = init.getElse();
			JDDNode leThen = init.getThen();
			JDDNode invElse = involution2(leElse,scale,VARIABLESsob);
			JDDNode invThen = involution2(leThen,scale,VARIABLESsob);
			JDDNode vart = 	VARIABLESsob.getVar(index);
			JDD.Ref(vart);
			JDDNode ONED = JDD.Constant(1d);
			JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
			JDD.Ref(vart);
			vart = JDD.Apply(JDD.TIMES, vart, invThen);
			varf = JDD.Apply(JDD.TIMES, varf, invElse);
			JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
			return res;
		}	 
		else  // constant
		{
			double valeur = init.getValue();
			int lIndice = scale.indexOf(valeur);
			System.out.println("lIndice: " + lIndice + " valeur:" + valeur);
			lIndice = scale.size()-lIndice-1;
			return JDD.Constant(scale.get(lIndice));
		}
	}
	
	// TODO necessity computation
	public JDDNode necessite(JDDNode init, Vector<Double> scale){
		if (!init.isConstant()) { // not a leaf
			int index =  init.getIndex(); // get its index
			if (!_ind2VarName.get(index).endsWith("'")) // non primed variable
			{
				JDDNode leElse = init.getElse();
				JDDNode leThen = init.getThen();
				JDDNode invElse = necessite(leElse,scale);
				JDDNode invThen = necessite(leThen,scale);
				JDDNode vart = varsCUDD.getVar(index);
				JDD.Ref(vart);
				JDDNode ONED = JDD.Constant(1d);
				JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
				JDD.Ref(vart);
				vart = JDD.Apply(JDD.TIMES, vart, invThen);
				varf = JDD.Apply(JDD.TIMES, varf, invElse);
				JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
				return res;
			}
			else // primed variable
			{
				JDDNode leElse = init.getElse();
				JDDNode leThen = init.getThen();
				if (!(leElse.isConstant() && leThen.isConstant()))
					System.err.println("PROBLEME DANS LA FUNCTION NECESSITE DANS PARSECUDD: il y a des variables après la variable primée");
				JDDNode invElse = necessite(leElse,scale);
				JDDNode invThen = necessite(leThen,scale);
				JDDNode vart = varsCUDD.getVar(index);
				JDD.Ref(vart);
				JDDNode ONED = JDD.Constant(1d);
				JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
				JDD.Ref(vart);
				vart = JDD.Apply(JDD.TIMES, vart, invElse);
				varf = JDD.Apply(JDD.TIMES, varf, invThen);
				JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
				return res;
			}
		}
		else  // constant
		{
			double valeur = init.getValue();
			int lIndice = scale.indexOf(valeur);
			lIndice = scale.size()-lIndice-1;
			return JDD.Constant(scale.get(lIndice));
		}
	}
	
	
	// TODO possibilistic translation for observation functions
	public JDDNode possTradO(JDDNode leJDD){
		if (!leJDD.isConstant()) { 
			int index =  leJDD.getIndex(); 
			JDDNode leElse = leJDD.getElse();
			JDDNode leThen = leJDD.getThen();
			if (!leElse.isConstant() && !leThen.isConstant()) {
				JDDNode normElse = possTradO(leElse);
				JDDNode normThen = possTradO(leThen);
				JDDNode vart;
				if (_ind2ObsvarName.containsKey(index))
				{
					vart = obsvarName2obsvar.get(_ind2ObsvarName.get(index)).getVar(0);
				}
				else
				{
					vart = varName2var.get(_ind2VarName.get(index)).getVar(0);
				}
				//JDDNode vart = varsCUDD.getVar(index); 
				// TODO!! ATTENTION!!! ICI L'INDEX CORRESPOND AU NUM DANS VARCUDD, MAIS CA SERA PLUS LE CAS POUR LES OBSERVATIONS
				JDD.Ref(vart);
				JDDNode ONED = JDD.Constant(1d);
				JDDNode varf = JDD.Apply(JDD.MINUS, ONED , vart);
				JDD.Ref(vart);
				vart = JDD.Apply(JDD.TIMES, vart, normThen);
				varf = JDD.Apply(JDD.TIMES, varf, normElse);
				JDDNode res = JDD.Apply(JDD.PLUS, vart, varf);
				return res;
			} 
			else if (index>=nStateVars && leElse.isConstant() && leThen.isConstant()) {
				double EE = leElse.getValue();
				double TT = leThen.getValue();
				if (EE > TT) {
					JDDNode vart;
					if (_ind2ObsvarName.containsKey(index))
					{
						vart = obsvarName2obsvar.get(_ind2ObsvarName.get(index)).getVar(0);
					}
					else
					{
						vart = varName2var.get(_ind2VarName.get(index)).getVar(0);
					}
					//JDDNode vart = varsCUDD.getVar(index);
					JDD.Ref(vart);
					JDDNode ONED = JDD.Constant(1d);
					JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
					JDD.Ref(vart);
					JDDNode JDDTT = JDD.Constant(TT);
					vart = JDD.Apply(JDD.TIMES, vart, JDDTT);
					JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
					return lADD;
				} else if (EE<TT) {
					JDDNode vart;
					if (_ind2ObsvarName.containsKey(index))
					{
						vart = obsvarName2obsvar.get(_ind2ObsvarName.get(index)).getVar(0);
					}
					else
					{
						vart = varName2var.get(_ind2VarName.get(index)).getVar(0);
					}
					//JDDNode vart = varsCUDD.getVar(index);
					JDDNode JDDEE = JDD.Constant(EE);
					JDD.Ref(vart);
					JDDNode ONED = JDD.Constant(1d);
					JDDNode varf = JDD.Apply(JDD.MINUS,ONED,vart);
					varf = JDD.Apply(JDD.TIMES,JDDEE,varf);
					JDD.Ref(vart);
					JDDNode lADD = JDD.Apply(JDD.PLUS, vart, varf);
					return lADD;
				}
				else
				{
					JDDNode res = JDD.Constant(1d);
					return res;
				}
			}
			else
			{
				System.err.println("PROBLEME DANS TRAD DANS PARSECUDD: cas pas pris en compte");
				return null;
			}
		} else {
			System.out.println("ATTENTION: l'ADD EST UNE FEUILLE!");
			JDDNode res = JDD.Constant(1d);
			return res;
		}
	}
	
	// TODO support of an ADD
	public Vector<String> supportCube(JDDNode leCube)
	{
		Vector<String> result = new Vector<String>(); 
		while(!leCube.isConstant())
		{
			int lIndex = leCube.getIndex();
			if (_ind2ObsvarName.containsKey(lIndex))
			{
				result.add(_ind2ObsvarName.get(lIndex));				
			}
			else
			{
				result.add(_ind2VarName.get(lIndex));
			}
			leCube = leCube.getThen();

		}
		return result; 
	}
	
	// error print functions
	private void error(int id)
	{
		System.out.println("Parse error at line #" + stream.lineno());
		//if (stream.ttype > 0)
		//		System.out.println("ttype = " + Character('a'));
		/* else */System.out.println("ttype = " + stream.ttype);
		System.out.println("sval = " + stream.sval);
		System.out.println("nval = " + stream.nval);
		System.out.println("ID = " + id);
		//System.exit(1); // TODO?
	}

	private void error(String errorMessage)
	{
		System.out.println("Parse error at " + stream.toString() + ": " + errorMessage);
		//System.exit(1);  // TODO?
	}
	
	
	// function to get the memory usage during the use of the parser
	static double getMemRatio(){
		// MEMUSED = MemTotal - (Buffers + Cached + MemFree)
        	String strRAMTOT[];
        	String strMEM[];
        	double RAMTOTGO = 0;
        	double MEMGO = 0;
        	double kb2go = 9.5367431640625*Math.pow(10,-7);
        	try{
			RandomAccessFile mFile = new RandomAccessFile("/proc/meminfo", "r");
			// Open the file for reading
        		mFile.seek(0);
            		// print the line
            		String RAMTOT = mFile.readLine();
            		strRAMTOT = RAMTOT.split(" ");
            		double RAMTOTKB = Double.parseDouble(strRAMTOT[strRAMTOT.length-2]);
            		RAMTOTGO = RAMTOTKB*kb2go;
            		//1
            		String MEM = mFile.readLine();
            		//System.out.println(MEM);
            		strMEM = MEM.split(" ");
            		double MEMKB = Double.parseDouble(strMEM[strMEM.length-2]);
            		//2
            		MEM = mFile.readLine();
            		strMEM = MEM.split(" ");
            		MEMKB = MEMKB + Double.parseDouble(strMEM[strMEM.length-2]);
            		//3
            		MEM = mFile.readLine();
            		strMEM = MEM.split(" ");
            		MEMKB = MEMKB + Double.parseDouble(strMEM[strMEM.length-2]);
           
            		MEMGO = MEMKB*kb2go;
            		mFile.close();
        	}
        	catch (IOException ex) {
            		ex.printStackTrace();
        	}
        	double propRAM = (RAMTOTGO-MEMGO)*100.0/RAMTOTGO;
		return propRAM;
	}
	
	// POMDP parser
	public void parsePOMDP(boolean fullyObservable)
	{
		try
		{
			boolean primeVarsCreated = false;
			while (true)
			{
				System.out.println(" MEMOIRE: " + getMemRatio());
				if (!primeVarsCreated && nStateVars > 0 && (fullyObservable || nObsVars > 0))
				{
					primeVarsCreated = true;
					createPrimeVars();
				}
				stream.nextToken();

				switch (stream.ttype)
				{
				case '(':
					stream.nextToken();
					if (stream.sval.compareTo("variables") == 0)
					{
						parseVariables();
					}
					else if (stream.sval.compareTo("observations") == 0)
					{
						parseObservations();
					}
					else
						error("Expected \"variables\" or \"observations\"");
					break;
				case StreamTokenizer.TT_WORD:
					if (stream.sval.compareTo("unnormalized") == 0)
					{
						unnormalized = true;
						break;
					}
					else if (stream.sval.compareTo("unnormalised") == 0)
					{
						unnormalized = true;
						break;
					}
					else if (stream.sval.compareTo("dd") == 0)
					{
						parseDDdefinition();
						break;
					}
					else if (stream.sval.compareTo("action") == 0)
					{
						parseAction();
						break;
					}
					else if (stream.sval.compareTo("reward") == 0)
					{
						parseReward();
						break;
					}
					else if (stream.sval.compareTo("discount") == 0)
					{
						parseDiscount();
						break;
					}
					else if (stream.sval.compareTo("horizon") == 0)
					{
						parseHorizon();
						break;
					}
					else if (stream.sval.compareTo("tolerance") == 0)
					{
						parseTolerance();
						break;
					}
					else if (stream.sval.compareTo("init") == 0)
					{
						parseInit();
						break;
					}
					error("Expected \"unnormalized\" or \"dd\" or \"action\" or \"reward\"");
				case StreamTokenizer.TT_EOF:
					// TODO
					/*
					// set valNames for actions
					String[] actNamesArray = new String[actNames.size()];
					for (int actId = 0; actId < actNames.size(); actId++)
					{
						actNamesArray[actId] = (String) actNames.get(actId);
					}
					//Global.setValNames(Global.valNames.length + 1, actNamesArray);

					// set varDomSize with extra action variable
					int[] varDomSizeArray = new int[Global.varDomSize.length + 1];
					for (int varId = 0; varId < Global.varDomSize.length; varId++)
					{
						varDomSizeArray[varId] = Global.varDomSize[varId];
					}
					varDomSizeArray[varDomSizeArray.length - 1] = actNamesArray.length;
					Global.setVarDomSize(varDomSizeArray);
					*/
					return;
				default:
					error(3);
				}
			}

		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	
	
	
	public void parseVariables() 
	{
		try
		{
			//parseVAR
			_varName2Ind = new TreeMap<String, Integer>(); // dans cudd, une var est un int
			_ind2VarName = new TreeMap<Integer,String>();
			varsCUDD = new JDDVars(); // toutes les vars (d'état)
			allVariables = new JDDVars(); // toutes les vars vraiment
			vars = new JDDVars(); // utilisé pour swapper
			varName2var = new TreeMap<String, JDDVars>();
			while (true)
			{
				if (stream.nextToken() == '(')
				{
					if (StreamTokenizer.TT_WORD != stream.nextToken())
						error("Expected a variable name");
					if (varNames.contains(stream.sval))
						error("Duplicate variable name");
					varNames.add(stream.sval);
					_varName2Ind.put(stream.sval, nStateVars); // dans cudd, une var est un int
					_ind2VarName.put(nStateVars,stream.sval);
					
					JDDNode VARIABLE = JDD.Var(nStateVars); // creation des variables non primées (pas ref)
					//JDD.Ref(VARIABLE);
					
					varsCUDD.addVar(VARIABLE); // toutes les vars (d'état)
					
					vars.addVar(VARIABLE); // utilisé pour swapper
					
					allVariables.addVar(VARIABLE); // toutes les vars vraiment
					
					JDDVars varp = new JDDVars();
					varp.addVar(VARIABLE);
					varName2var.put(stream.sval, varp ); // pour le max abstract
										
					Vector<String> varValNames = new Vector<String>();
					while (true)
					{
						if (StreamTokenizer.TT_WORD == stream.nextToken())
						{
							if (varValNames.contains(stream.sval))
								error("Duplicate value name");
							varValNames.add(stream.sval);
						}
						else if (stream.ttype == ')')
						{
							break;
						}
						else
						{
							error(4);
						}
					}
					valNames.add(varValNames);
					nStateVars++;
				}
				else if (stream.ttype == ')')
				{
					break;
				}
				else
				{
					error("");
				}
			} // parseVar
		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	
	public void createPrimeVars()
	{
		// create prime variables
		int nVars = varNames.size();
		primedVars = new JDDVars(); 
		for (int i = 0; i < nVars; i++)
		{
			varNames.add((String) varNames.get(i) + "'");
			valNames.add((Vector) valNames.get(i));
	
			_varName2Ind.put(varNames.get(i) + "'",nStateVars+i);
			_ind2VarName.put(nStateVars+i,varNames.get(i) + "'");
			
			JDDNode VARIABLE = JDD.Var(nStateVars+i);
			
			varsCUDD.addVar(VARIABLE); // toutes les vars
			primedVars.addVar(VARIABLE); // utilisé pour swapper
			
			allVariables.addVar(VARIABLE); // toutes les vars vraiment
			
			JDDVars varp = new JDDVars();
			varp.addVar(VARIABLE);
			varName2var.put(varNames.get(nStateVars+i), varp ); // pour le max abstract
		}
	}
	
	
	public void parseObservations()  
	{
		try
		{
			_obsvarName2Ind = new TreeMap<String,Integer>(); // dans cudd, une var est un int
			_ind2ObsvarName = new TreeMap<Integer,String>();
			
			obsvarCUDD = new JDDVars(); // toutes les vars
			obsvar = new JDDVars(); // utilisé pour swapper
			obsvarName2obsvar = new TreeMap<String, JDDVars>();
			while (true)
			{
				if (stream.nextToken() == '(')
				{
					if (StreamTokenizer.TT_WORD != stream.nextToken())
						error("Expected a variable name");
					if (obsvarNames.contains(stream.sval))
						error("Duplicate variable name");
					String obsNAME = stream.sval;
					obsvarNames.add(obsNAME);  
					_obsvarName2Ind.put(obsNAME, nObsVars+2*nStateVars); // dans cudd, une var est un int
					_ind2ObsvarName.put(nObsVars+2*nStateVars, obsNAME);
					
					JDDNode VARIABLE = JDD.Var(nObsVars+2*nStateVars); // creation des variables non primées (pas ref)
					
					obsvarCUDD.addVar(VARIABLE); // toutes les vars
					obsvar.addVar(VARIABLE); // utilisé pour swapper
					
					allVariables.addVar(VARIABLE); // toutes les vars vraiment
					
					JDDVars varp = new JDDVars();
					varp.addVar(VARIABLE);
					obsvarName2obsvar.put(obsNAME, varp ); // pour le max abstract
			
					Vector<String> varValNames = new Vector<String>();
					while (true)
					{
						if (StreamTokenizer.TT_WORD == stream.nextToken())
						{
							if (varValNames.contains(stream.sval))
								error("Duplicate value name");
							varValNames.add(stream.sval);
						}
						else if (stream.ttype == ')')
							break;
						else
							error(4);
					}
					obsvalNames.add(varValNames);
					nObsVars++;
				}
				else if (stream.ttype == ')')
				{
					break;
				}
				else
					error("");
			}
			
			
			// create prime obs variables
			int nVars = obsvarNames.size();
			primedObsvar = new JDDVars(); 
			for (int i = 0; i < nVars; i++)
			{
				obsvarNames.add((String) obsvarNames.get(i) + "'");
				obsvalNames.add((Vector) obsvalNames.get(i));
		
				_obsvarName2Ind.put(obsvarNames.get(i) + "'",nObsVars+i+2*nStateVars);
				_ind2ObsvarName.put(nObsVars+i+2*nStateVars,obsvarNames.get(i) + "'");

				JDDNode VARIABLE = JDD.Var(nObsVars+i+2*nStateVars);
				
				obsvarCUDD.addVar(VARIABLE); // toutes les vars
				primedObsvar.addVar(VARIABLE); // utilisé pour swapper
				
				allVariables.addVar(VARIABLE); // toutes les vars vraiment
				
				JDDVars varp = new JDDVars();
				varp.addVar(VARIABLE);
				obsvarName2obsvar.put(obsvarNames.get(nObsVars+i), varp ); // pour le max abstract
			}
			
			
		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	public void parseDDdefinition()
	{
		try
		{
			if (StreamTokenizer.TT_WORD != stream.nextToken())
				error("Expected a dd name");
			String ddName = stream.sval;
			if (existingDds.get(ddName) != null)
				error("Duplicate dd name");
			JDDNode dd = parseDDcudd();
			existingDds.put(ddName, dd);
			stream.nextToken();
			//System.out.println(stream);
			if (stream.sval.compareTo("enddd") != 0)
				error("Expected \"enddd\"");
		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	
	public JDDNode parseDDcudd()
	{
		JDDNode leZERO = JDD.Constant(0d);
		
		JDDNode dd = null;
		try
		{
			// parse DD
			if (stream.nextToken() == '(')
			{

				// parse DDnode
				if (StreamTokenizer.TT_WORD == stream.nextToken())
				{

					// existingDd
					dd = existingDds.get(stream.sval);
					if (dd != null)
					{
						if (stream.nextToken() != ')')
							error("Expected ')'");
					}
					else if (dd == null)	// it's not an existing dd so perhaps it's a variable
					{
						Object varIdO = _varName2Ind.get(stream.sval);
						Boolean stateVar = null;
						if ( varIdO == null)
						{
							varIdO = _obsvarName2Ind.get(stream.sval);
							if ( varIdO == null)
							{
								error("Not an existing dd nor an existing variable");
							}
							else
							{
								stateVar = false;
							}
						}
						else
						{
							stateVar = true;
						}
							
						int varId = (Integer) varIdO;
						// parse values
						Vector varValNames;
						if (stateVar)
						{
							varValNames = (Vector) valNames.get(varId);
						}
						else
						{
							varValNames = (Vector) obsvalNames.get(varId-2*nStateVars);
						}
						JDDNode[] children = new JDDNode[varValNames.size()];
						for (int i = 0; i < children.length; i++)
						{
							children[i] = leZERO; // CA BUG LA OU ON INSTANCIE DES CONSTANTES **$$**
							JDD.Ref(children[i]);
						}
						Vector valNamesSoFar = new Vector();
						while (true)
						{
							if (stream.nextToken() == '(')
							{
								stream.nextToken();
								if (valNamesSoFar.contains(stream.sval))
									error("Duplicate child");
								int valId = varValNames.indexOf(stream.sval);
								if (valId == -1)
									error("Invalid value");
								
								JDD.Deref(children[valId]);
								children[valId] = parseDDcudd();
								if (stream.nextToken() != ')')
									error("Expected ')'");
							}
							else if (stream.ttype == ')')
							{
								break;
							}
							else
								error("Expected ')' or '('");
						}
						if (stateVar)
						{
							dd = varsCUDD.getVar(varId);
							JDD.Ref(dd);
						}
						else
						{
							dd = obsvarCUDD.getVar(varId-2*nStateVars); // getVar récupere la var dans ce vecteur de var...
							// donc ce n'est pas l'ID de la variable qu'il attend
							// mais bien après combien de variables d'observations elle a été rajoutée..
							JDD.Ref(dd);
						}
						dd = JDD.ITE(dd, children[0], children[1]);
					}
				}

				// parse leaf node
				else if (StreamTokenizer.TT_NUMBER == stream.ttype)
				{
					dd = JDD.Constant(stream.nval); //**$$**
					if (stream.nextToken() != ')')
						error("Expected ')'");
				}

				// Invalid dd
				else
					error("Invalid DDnode or DDleaf");
			}

			// arithmetic operation
			else if (stream.ttype == '[')
			{

				// parse operator
				int operator = stream.nextToken();

				// multiplication
				if (operator == '*')
				{
					dd = JDD.Constant(1d);
					while (stream.nextToken() != ']')
					{
						stream.pushBack();
						JDDNode newDd = parseDDcudd();
						dd = JDD.Apply(JDD.TIMES, dd, newDd);
					}
				}

				// addition
				else if (operator == '+')
				{
					dd = JDD.Constant(0d);
					while (stream.nextToken() != ']')
					{
						stream.pushBack();
						JDDNode newDd = parseDDcudd();
						dd = JDD.Apply(JDD.PLUS, dd, newDd);
					}
				}

				// PROBABILISTIC normalisation removed
				/*
				else if (operator == '#')
				{
					stream.nextToken();
					int[] vars = new int[1];
					vars[0] = varNames.indexOf(stream.sval) + 1;
					if (vars[0] == 0)
						error("Unknown variable name");
					DD[] dds = new DD[1];
					dds[0] = OP.reorder(parseDD());
					dd = OP.div(dds[0], OP.addMultVarElim(dds, vars));
					if (stream.nextToken() != ']')
						error("Expected ']'");
				}
*/
				else
					error("Expected '*' or '+' or '#'");
			}
			else
				error("Expected '(' or '['");

		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
		JDD.Deref(leZERO);
		return dd;
	}
	
	public void parseAction()
	{
		try
		{
			// parse action name
			stream.nextToken();

			if (actNames.contains(stream.sval))
				error("Duplicate action name");
			else
				actNames.add(stream.sval);
			actCosts.add(JDD.Constant(0d));
			
			JDDNode[] cpts = new JDDNode[nStateVars];

			// parse cpts
			while (true)
			{
				// endaction
				stream.nextToken();
				if (stream.sval.compareTo("endaction") == 0)
					break;

				// cost
				else if (stream.sval.compareTo("cost") == 0)
				{
					JDDNode dd = parseDDcudd();   
					JDD.Deref(actCosts.get(actCosts.size() - 1));
					actCosts.set(actCosts.size() - 1, dd);
				}

				// observation function
				else if (stream.sval.compareTo("observe") == 0)
				{
					// CPT VARIABLES DOBSERVATION
					JDDNode[] obsCPTs = new JDDNode[nObsVars]; 
					while (true)
					{

						// endobserve
						stream.nextToken();
						if (stream.sval.compareTo("endobserve") == 0)
							break;

						// obs cpt
						else
						{
							Object varIdO = _obsvarName2Ind.get(stream.sval);
							if (varIdO == null) 
								error("Invalid observation name"); 
							
							int varId = (Integer) varIdO;
							obsCPTs[varId - 2*nStateVars] = parseDDcudd();

							// PROBABILISTIC NORMALIZATION REMOVED
							/*
							DD[] dds = new DD[1];
							dds[0] = obsCPTs[varId - nStateVars];
							int[] vars = new int[1];
							vars[0] = varId + varNames.size() / 2 + 1;
							DD normalizationFactor = OP.addMultVarElim(dds, vars);
							if (unnormalized)
								obsCPTs[varId - nStateVars] =
										OP.div(obsCPTs[varId - nStateVars], normalizationFactor);
							else if (OP.maxAll(OP.abs(OP.sub(normalizationFactor, DD.one))) > 1e-8)
								error("Unnormalized cpt for " + varNames.get(varId) + "'");
							*/
						}
					}
					actObserve.add(obsCPTs);
				}

				// cpt
				else
				{
					// CPT VARIABLES DETATS
					int varId = varNames.indexOf(stream.sval); // les indices correspondent à ceux de _varName2Ind
					if (varId == -1 || varId >= nStateVars)
						error("Invalid variable name");
					cpts[varId] = parseDDcudd();

					// normalize
					/*
					DD[] dds = new DD[1];
					dds[0] = cpts[varId];
					int[] vars = new int[1];
					vars[0] = varId + varNames.size() / 2 + 1;
					DD normalizationFactor = OP.addMultVarElim(dds, vars);
					if (unnormalized)
						cpts[varId] = OP.div(cpts[varId], normalizationFactor);
					else if (OP.maxAll(OP.abs(OP.sub(normalizationFactor, DD.one))) > 1e-8)
						error("Unnormalized cpt for " + varNames.get(varId) + "'");
					*/
				}
			}
			actTransitions.add(cpts);

		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	public void parseReward()
	{
		reward = parseDDcudd();
	}

	public void parseInit()
	{
		init = parseDDcudd();
	}

	public void parseDiscount()
	{
		try
		{
			if (stream.nextToken() != StreamTokenizer.TT_NUMBER)
				error("Expected a number");
			discount = JDD.Constant(stream.nval);
		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	public void parseHorizon()
	{
		try
		{
			if (stream.nextToken() != StreamTokenizer.TT_NUMBER)
				error("Expected a number");
			horizon = JDD.Constant(stream.nval);
		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}

	public void parseTolerance()
	{
		try
		{
			if (stream.nextToken() != StreamTokenizer.TT_NUMBER)
				error("Expected a number");
			tolerance = JDD.Constant(stream.nval);
		} catch (IOException e)
		{
			System.out.println("Error: IOException\n");
			//System.exit(1);
		}
	}
	
}
