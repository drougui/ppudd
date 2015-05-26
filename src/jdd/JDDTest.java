//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package jdd;


public class JDDTest extends DebugJDD
{
	// arbitrary function
	static JDDNode function(JDDNode aADD, JDDNode anotherADD)
	{
		JDD.Ref(aADD);
		JDDNode tempADD = JDD.Apply(JDD.TIMES, aADD ,anotherADD ); // anotherADD deref, tempADD ref
		JDDNode res = JDD.Apply(JDD.MINUS, tempADD ,aADD ); // tempADD deref, aADD deref
		return res;
	}
	
	// plot a ADD
	static void afficheDdNodeMDP(JDDNode dd, int prof, char TE)
	{
		int index = dd.getIndex(); 
		if (dd.isConstant()==true && TE=='t')
		{
			System.out.print("  ");
			System.out.print("val: " + dd.getValue() + " ");
			System.out.println();
		}
		else if (dd.isConstant()==true && TE=='e')
		{
			System.out.print("  ");
			for (int i=0; i<prof;i++)
			{
				System.out.print("        ");
			}
			System.out.print("val: " + dd.getValue() + " ");
			System.out.println();
		}
		else if (dd.isConstant()==false && TE=='t')
		{
		     System.out.print("  ");
		     System.out.print("id: " + index + " ");
		     afficheDdNodeMDP(dd.getThen(), prof+1,'t');
		     afficheDdNodeMDP(dd.getElse(), prof+1,'e');
		}
		else
		{
			System.out.print("  ");
			for (int i=0; i<prof;i++)
			{
				System.out.print("        ");
			}
			System.out.print("id: " + index + " ");
			afficheDdNodeMDP(dd.getThen(), prof+1,'t');
			afficheDdNodeMDP(dd.getElse(), prof+1,'e');
		}
	}
	
	// test the CUDD wrapper
	public static void main(String[] args)
	{
		System.out.println("\nTest program for JDD\n====================");

		// initialize CUDD
		System.out.println("\nINITIALIZE CUDD");
		JDD.InitialiseCUDD();

		// declaration of 3 ADDs
		JDDNode a, b, c; 
		
		// set up 3 variables		
		JDDVars vars;	
		vars = new JDDVars();
		vars.addVar(JDD.Var(0));
		vars.addVar(JDD.Var(1));
		vars.addVar(JDD.Var(2));	
		
		// corresponding ADDs stored in a, b and c, 
		// and referenced. 
		a = vars.getVar(0);
		JDD.Ref(a);
		b = vars.getVar(1);
		JDD.Ref(b);
		c = vars.getVar(2);
		JDD.Ref(c);
		
		System.out.println("ADD a:");
		afficheDdNodeMDP(a, 0, 't');
		System.out.println("ADD b:");
		afficheDdNodeMDP(b, 0, 't');
		System.out.println("ADD c:");
		afficheDdNodeMDP(c, 0, 't');
		
		// refs counts 1
		System.out.println("\n[step 1] a, b and c reference counts: ");
		DebugJDD.REFCOUNT(a);
		DebugJDD.REFCOUNT(b);
		DebugJDD.REFCOUNT(c);
		System.out.println("\n");
		
		// operations
		JDDNode res = function(a,b); // a deref, b deref, res ref
		res = JDD.Apply(JDD.MINUS, c, res); 
		
		System.out.println("\nresulting ADD:");
		afficheDdNodeMDP(res, 0, 't');
		
		// refs counts 2
		System.out.println("\n[step 2] a, b and c reference counts: ");
		DebugJDD.REFCOUNT(a);
		DebugJDD.REFCOUNT(b);
		DebugJDD.REFCOUNT(c);
		System.out.println("\n");
		
		JDD.Deref(res);
		vars.derefAll();

		// refs counts 3
		System.out.println("\n[step 3] a, b and c reference counts: ");
		DebugJDD.REFCOUNT(a);
		DebugJDD.REFCOUNT(b);
		DebugJDD.REFCOUNT(c);
		System.out.println("\n");
		
		System.out.println("\nCLOSE CUDD");
		JDD.CloseDownCUDD();
	}
}