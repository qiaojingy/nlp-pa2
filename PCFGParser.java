package cs224n.assignment;

import cs224n.ling.Tree;
import java.util.*;

import cs224n.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
    private Grammar grammar;
    private Lexicon lexicon;

    public void train(List<Tree<String>> trainTrees) {
        // TODO: before you generate your grammar, the training trees
        // need to be binarized so that rules are at most binary
        lexicon = new Lexicon(trainTrees);
	TreeAnnotations treeAnnotations = new TreeAnnotations();
	for (int t = 0; t < trainTrees.size(); t++) {
	    trainTrees.set(t, treeAnnotations.annotateTree(trainTrees.get(t)));
	}
        grammar = new Grammar(trainTrees);
        System.out.println("Phase Training Completed");
    }

    public Tree<String> getBestParse(List<String> sentence) {
        // TODO: implement this method
        List<List<Counter<String>>> scores = new ArrayList<List<Counter<String>>>();
        for (int i = 0; i<sentence.size()+1; i++){
            List<Counter<String>> tempList = new ArrayList<Counter<String>>();
            for (int j=i; j<sentence.size()+1; j++){
                tempList.add(new Counter<String>());
            }
            scores.add(tempList);
        }

        List<List<Dictionary<String, Triplet<Integer, String, String>>>> back = new ArrayList<List<Dictionary<String, Triplet<Integer, String, String>>>>();
        for (int i = 0; i<sentence.size()+1; i++){
            List<Dictionary<String, Triplet<Integer, String, String>>> tempList = new ArrayList<Dictionary<String, Triplet<Integer, String, String>>>();
            for (int j=i; j<sentence.size()+1; j++){
                tempList.add(new Hashtable<String, Triplet<Integer, String, String>>());
            }
            back.add(tempList);
        }
        
	System.out.println("Phase Preprocessing Completed");

        for (int i = 0; i < sentence.size(); i++){
            String word = sentence.get(i);
            double bestScore = Double.NEGATIVE_INFINITY;
            for (String tag : lexicon.getAllTags()) {
	        double score = lexicon.scoreTagging(word, tag);
                if (score > 0){
                    scores.get(i).get(1).setCount(tag, score);
                }
            }
            boolean added = true;
	    /* Handle unaries, can be improved? */
            while (added){
 	        added = false;
                Set<String> keySet = new HashSet<String>();
                for (String key : scores.get(i).get(1).keySet()){
                    keySet.add(key);
                }
	        for (String B : keySet){
	            for (Grammar.UnaryRule unaryRule : grammar.getUnaryRulesByChild(B)){
	 	        double score = unaryRule.getScore();
	 	        if (score > 0){
	 	            double prob = score * scores.get(i).get(1).getCount(B);
			    String A = unaryRule.getParent();
			    if (prob > scores.get(i).get(1).getCount(A)){
			        scores.get(i).get(1).setCount(A, prob);
   				back.get(i).get(1).put(A, new Triplet(-1, B, "s"));
		                added = true;
			    }
		        }
		     }
	        }
           }
           
        }
        
        for (int span = 2; span <= sentence.size(); span++){
            for (int begin = 0; begin <= sentence.size() -  span; begin++){
                int end = begin + span;
		for (int split = begin + 1; split <= end - 1; split++){
		    for (String B : scores.get(begin).get(split-begin).keySet()){
		        List<Grammar.BinaryRule> leftRules = grammar.getBinaryRulesByLeftChild(B);
                        if (leftRules.size() == 0){
                             continue;
                        }
			Set<String> keySetRight = scores.get(split).get(end-split).keySet();
                        for (Grammar.BinaryRule rule1 : leftRules){
			    String C = rule1.getRightChild();
			    if (keySetRight.contains(C)){
			        double prob = scores.get(begin).get(split-begin).getCount(B) * scores.get(split).get(end-split).getCount(C) * rule1.getScore();
			        String A = rule1.getParent();
                                if (prob > scores.get(begin).get(span).getCount(A)){
	                            scores.get(begin).get(span).setCount(A, prob);
	                            back.get(begin).get(span).put(A, new Triplet(split, B, C));
				}
			    }
			}
	            }
		}
		boolean added = true;
		while (added){
 	            added = false;
                    Set<String> keySet = new HashSet<String>();
                    for (String key : scores.get(begin).get(span).keySet()){
                        keySet.add(key);
                    }
	            for (String B : keySet){
	                for (Grammar.UnaryRule unaryRule : grammar.getUnaryRulesByChild(B)){
	 	            double score = unaryRule.getScore();
	 	            if (score > 0){
	 	                double prob = score * scores.get(begin).get(span).getCount(B);
		       	        String A = unaryRule.getParent();
			        if (prob > scores.get(begin).get(span).getCount(A)){
			            scores.get(begin).get(span).setCount(A, prob);
   			            back.get(begin).get(span).put(A, new Triplet(-1, B, "s"));
		                    added = true;
			        }
		            }
		         }
	            }
                 }
            }
        }
	
	System.out.println("Phase Calculation Completed");

        class TreeBuilder{
            private List<List<Dictionary<String, Triplet<Integer, String, String>>>> backtag; 
            private List<String> sentenceToParse;
            public TreeBuilder(List<List<Dictionary<String, Triplet<Integer, String, String>>>> backtag_, List<String> sentenceToParse_){
                this.backtag = backtag_;
                this.sentenceToParse = sentenceToParse_;
            }
        
            public Tree<String> buildTree(int begin, int span, String tag){
	       Tree<String> subParse = new Tree<String>(tag);
	       Tree<String> current = subParse;
	       Triplet<Integer, String, String> tripletIn = backtag.get(begin).get(span).get(tag);
               String newTag = "";
               if (tripletIn != null){
	           while ((Integer) tripletIn.getFirst() == -1){
                       newTag = (String) tripletIn.getSecond();
	               Tree<String> subSubParse = new Tree<String>(newTag);
	               current.setChildren(Collections.singletonList(subSubParse));
	               current = subSubParse;
                       tripletIn = backtag.get(begin).get(span).get(newTag);
                       if (tripletIn == null){
                           break;
                       }
	           }
               }
	       if (tripletIn == null){
	           if (span != 1){
	               System.out.println("Something wrong happened");
	               return null;
	           }
	           Tree<String> subSubParse = new Tree<String>(sentenceToParse.get(begin));
	           current.setChildren(Collections.singletonList(subSubParse));
	           return subParse;
	       }  
               int split = (Integer) tripletIn.getFirst();
               Tree<String> leftTree = buildTree(begin, split-begin, (String) tripletIn.getSecond());
	       Tree<String> rightTree = buildTree(split, span - (split - begin), (String) tripletIn.getThird());
	       List<Tree<String>> children = new ArrayList<Tree<String>>();
	       children.add(leftTree);
	       children.add(rightTree);
               current.setChildren(children);
	       return subParse;
            }
        }
        
        TreeBuilder treeBuilder = new TreeBuilder(back, sentence);
        String tag = "ROOT";
	/* String tag = scores.get(0).get(sentence.size()).argMax(); */
        /*if (rootScore == scores.get(0).get(sentence.size()).getCount(tag)){
            tag = "ROOT";
        }*/
	Tree<String> parseTree = treeBuilder.buildTree(0, sentence.size(), tag);
        /*parseTree = new Tree<String>("S", Collections.singletonList(parseTree));*/
        /*parseTree = new Tree<String>("ROOT", Collections.singletonList(parseTree));*/
        TreeAnnotations treeAnnotations = new TreeAnnotations();
        parseTree = treeAnnotations.unAnnotateTree(parseTree);
	System.out.println("Phase Build Tree Completed");
        return parseTree;
    }
}
