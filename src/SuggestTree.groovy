/*
 * Copyright (c) 2008-2014 Nicolai Diethelm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * (MIT License)
 */

package src;

import java.util.*;

/**
 * An implementation of a suggest tree.
 */
public final class SuggestTree {
    
    private Node root = null;
    private final int k;
    private final Random random = new Random();
    
    /**
     * Creates a tree with the specified k-value, initially containing the
     * specified terms with the specified weights.
     * 
     * @throws IllegalArgumentException if k is less than 1 or one of the terms
     * is an empty string
     */
    public SuggestTree(int k, Map<String, Integer> weightedTerms) {
        if(k < 1)
            throw new IllegalArgumentException();
        this.k = k;
        Map.Entry<String, Integer>[] array = weightedTerms.entrySet().toArray(new Map.Entry[0]);
        Arrays.sort(array, new Comparator<Map.Entry<String, Integer>>() {

            @Override
            public int compare(Map.Entry<String, Integer> e1,
                               Map.Entry<String, Integer> e2) {
                return e1.getKey().compareTo(e2.getKey());
            }
        });
        buildBalancedTree(array, 0, array.length - 1);
    }
    
    private void buildBalancedTree(Map.Entry<String, Integer>[] weightedTerms,
                                   int fromIndex, int toIndex) {
        if(fromIndex <= toIndex) {
            int mid = (fromIndex + toIndex) / 2;
            Map.Entry<String, Integer> e = weightedTerms[mid];
            insert(e.getKey(), e.getValue());
            buildBalancedTree(weightedTerms, fromIndex, mid - 1);
            buildBalancedTree(weightedTerms, mid + 1, toIndex);
        }
    }
    
    /**
     * Returns the node with the top k highest-weighted terms that start with
     * the specified prefix, or null if the tree contains no term with the
     * prefix.
     * 
     * @throws IllegalArgumentException if the prefix is an empty string
     */
    public Node autocompleteSuggestionsFor(String prefix) {
        if(prefix.isEmpty())
            throw new IllegalArgumentException();
        int i = 0;
        Node n = root;
        while(n != null) {
            if(prefix.charAt(i) < n.firstChar)
                n = n.left;
            else if(prefix.charAt(i) > n.firstChar)
                n = n.right;
            else{
                i++;
                while(i < n.end) {
                    if(i == prefix.length())
                        return n;
                    else if(prefix.charAt(i) != n.term.charAt(i))
                        return null;
                    else
                        i++;
                }
                if(i < prefix.length())
                    n = n.mid;
                else
                    return n;
            }
        }
        return null;
    }
    
    /**
     * Inserts the specified term with the specified weight into the tree.
     * 
     * @throws IllegalArgumentException if the term is an empty string
     * @throws IllegalStateException if the tree already contains the term
     */
    public void insert(String term, int weight) {
        if(term.isEmpty())
            throw new IllegalArgumentException();
        if(root == null) {
            root = new Node(term, weight, 0, null);
            return;
        }
        int i = 0;
        Node n = root;
        while(true) {
            if(term.charAt(i) < n.firstChar) {
                if(n.left == null) {
                    n.left = new Node(term, weight, i, n);
                    insertIntoLists(n.left);
                    return;
                }else
                    n = n.left;
            }else if(term.charAt(i) > n.firstChar) {
                if(n.right == null) {
                    n.right = new Node(term, weight, i, n);
                    insertIntoLists(n.right);
                    return;
                }else
                    n = n.right;
            }else{
                i++;
                while(i < n.end) {
                    if(i == term.length() || term.charAt(i) != n.term.charAt(i))
                        n = split(n, i);
                    else
                        i++;
                }
                if(i < term.length()) {
                    if(n.mid == null) {
                        n.mid = new Node(term, weight, i, n);
                        insertIntoLists(n.mid);
                        return;
                    }else
                        n = n.mid;
                }else if(!n.isTerminal()) {
                    n.term = term;
                    n.weight = weight;
                    updateTermReferences(n);
                    insertIntoLists(n);
                    return;
                }else
                    throw new IllegalStateException();
            }
        }
    }
    
    private Node split(Node n, int position) {
        Node splitOff = new Node();
        splitOff.term = n.term;
        splitOff.firstChar = n.firstChar;
        splitOff.end = (short) position;
        splitOff.mid = n;
        splitOff.left = n.left;
        splitOff.right = n.right;
        splitOff.parent = n.parent;
        splitOff.list = (n.list.length < k) ? n.list : Arrays.copyOf(n.list, k);
        if(n == root)
            root = splitOff;
        else if(n == n.parent.left)
            n.parent.left = splitOff;
        else if(n == n.parent.right)
            n.parent.right = splitOff;
        else
            n.parent.mid = splitOff;
        if(n.left != null)
            n.left.parent = splitOff;
        if(n.right != null)
            n.right.parent = splitOff;
        n.firstChar = n.term.charAt(position);
        n.left = n.right = null;
        n.parent = splitOff;
        return splitOff;
    }
    
    private void updateTermReferences(Node changed) {
        for(Node n = changed;
                n != root && n == n.parent.mid && !n.parent.isTerminal();
                n = n.parent)
            n.parent.term = changed.term;
    }
    
    private void insertIntoLists(Node terminal) {
        Node n = (terminal.mid != null) ? terminal : terminal.parentInTrie();
        for( ; n != null; n = n.parentInTrie()) {
            if(n.list.length < k)
                n.list = Arrays.copyOf(n.list, n.list.length + 1);
            else if(n.list[k - 1].weight >= terminal.weight)
                return;
            int pos = n.list.length - 1;
            while(pos > 0 && n.list[pos - 1].weight < terminal.weight) {
                n.list[pos] = n.list[pos - 1];
                pos--;
            }
            n.list[pos] = terminal;
        }
    }
    
    /**
     * Reweights the specified term so that it has the specified new weight.
     * 
     * @throws NoSuchElementException if the tree contains no such term
     */
    public void reweight(String term, int newWeight) {
        Node n = terminalNodeOf(term);
        if(n == null)
            throw new NoSuchElementException();
        int oldWeight = n.weight;
        n.weight = newWeight;
        if(newWeight > oldWeight)
            updateListsIncreasedWeight(n);
        else if(newWeight < oldWeight)
            updateListsReducedWeight(n);
    }

    private Node terminalNodeOf(String term) {
        if(term.isEmpty())
            return null;
        int i = 0;
        Node n = root;
        while(n != null) {
            if(term.charAt(i) < n.firstChar)
                n = n.left;
            else if(term.charAt(i) > n.firstChar)
                n = n.right;
            else{
                i++;
                while(i < n.end) {
                    if(i == term.length() || term.charAt(i) != n.term.charAt(i))
                        return null;
                    else
                        i++;
                }
                if(i < term.length())
                    n = n.mid;
                else if(n.isTerminal())
                    return n;
                else
                    return null;
            }
        }
        return null;
    }

    private void updateListsIncreasedWeight(Node terminal) {
        int i = 0;
        Node n = (terminal.mid != null) ? terminal : terminal.parentInTrie();
        for( ; n != null; n = n.parentInTrie()) {
            while(i < k && n.list[i] != terminal)
                i++;
            if(i == k && n.list[i - 1].weight >= terminal.weight)
                return;
            int pos = (i < k) ? i : i - 1;
            while(pos > 0 && n.list[pos - 1].weight < terminal.weight) {
                n.list[pos] = n.list[pos - 1];
                pos--;
            }
            n.list[pos] = terminal;
        }
    }
    
    private void updateListsReducedWeight(Node terminal) {
        int i = 0;
        Node n = (terminal.mid != null) ? terminal : terminal.parentInTrie();
        for( ; n != null; n = n.parentInTrie()) {
            while(i < k && n.list[i] != terminal)
                i++;
            if(i == k)
                return;
            int pos = i;
            while(pos < n.list.length - 1
                    && n.list[pos + 1].weight > terminal.weight) {
                n.list[pos] = n.list[pos + 1];
                pos++;
            }
            n.list[pos] = terminal;
            if(pos == k - 1) {
                Node topUnlisted = n.topUnlistedTerm();
                if(topUnlisted != null && topUnlisted.weight > terminal.weight)
                    n.list[pos] = topUnlisted;
            }
        }
    }
    
    /**
     * Removes the specified term from the tree.
     * 
     * @throws NoSuchElementException if the tree contains no such term
     */
    public void remove(String term) {
        Node n = terminalNodeOf(term);
        if(n == null)
            throw new NoSuchElementException();
        if(n.mid != null) {
            n.term = n.mid.term;
            updateTermReferences(n);
            if(n.mid.left == null && n.mid.right == null) {
                Node merged = mergeWithChild(n);
                removeFromLists(n, merged.parentInTrie());
            }else
                removeFromLists(n, n);
        }else{
            Node parent = n.parentInTrie();
            Node replacement = delete(n);
            if(replacement != null)
                updateTermReferences(replacement);
            if(parent != null && !parent.isTerminal()
                    && parent.mid.left == null && parent.mid.right == null) {
                Node merged = mergeWithChild(parent);
                removeFromLists(n, merged.parentInTrie());
            }else
                removeFromLists(n, parent);
        }
    }
    
    private Node delete(Node n) {
        Node replacement;
        if(n.left == null)
            replacement = n.right;
        else if(n.right == null)
            replacement = n.left;
        else if(random.nextBoolean() == true) {
            replacement = n.right;
            if(replacement.left != null) {
                while(replacement.left != null)
                    replacement = replacement.left;
                replacement.parent.left = replacement.right;
                if(replacement.right != null)
                    replacement.right.parent = replacement.parent;
                replacement.right = n.right;
                n.right.parent = replacement;
            }
            replacement.left = n.left;
            n.left.parent = replacement;
        }else{ // symmetric
            replacement = n.left;
            if(replacement.right != null) {
                while(replacement.right != null)
                    replacement = replacement.right;
                replacement.parent.right = replacement.left;
                if(replacement.left != null)
                    replacement.left.parent = replacement.parent;
                replacement.left = n.left;
                n.left.parent = replacement;
            }
            replacement.right = n.right;
            n.right.parent = replacement;
        }
        if(replacement != null)
            replacement.parent = n.parent;
        if(n == root)
            root = replacement;
        else if(n == n.parent.left)
            n.parent.left = replacement;
        else if(n == n.parent.right)
            n.parent.right = replacement;
        else
            n.parent.mid = replacement;
        return replacement;
    }
    
    private Node mergeWithChild(Node n) {
        Node merged = n.mid;
        merged.firstChar = n.firstChar;
        merged.left = n.left;
        merged.right = n.right;
        merged.parent = n.parent;
        if(n == root)
            root = merged;
        else if(n == n.parent.left)
            n.parent.left = merged;
        else if(n == n.parent.right)
            n.parent.right = merged;
        else
            n.parent.mid = merged;
        if(n.left != null)
            n.left.parent = merged;
        if(n.right != null)
            n.right.parent = merged;
        return merged;
    }
    
    private void removeFromLists(Node terminal, Node firstList) {
        int i = 0;
        for(Node n = firstList; n != null; n = n.parentInTrie()) {
            while(i < k && n.list[i] != terminal)
                i++;
            if(i == k)
                return;
            Node topUnlisted = (n.list.length < k) ? null : n.topUnlistedTerm();
            for(int pos = i; pos < n.list.length - 1; pos++)
                n.list[pos] = n.list[pos + 1];
            if(topUnlisted != null)
                n.list[k - 1] = topUnlisted;
            else
                n.list = Arrays.copyOf(n.list, n.list.length - 1);
        }
    }
    
    }

/**
     * A tree node with a list of autocomplete suggestions.
     */
    public final class Node {
        
        private String term;
        private int weight;
        private char firstChar;
        private short end;
        private Node left, mid, right, parent;
        private ArrayList<Node> list;
        
        private Node() {}
        
        private Node(String term, int weight, int start, Node parent) {
            this.term = term;
            this.weight = weight;
            firstChar = term.charAt(start);
            end = (short) term.length();
            left = mid = right = null;
            this.parent = parent;
            list = new ArrayList();
            list.add(this);

        }
        
        private boolean isTerminal() {
            return end == term.length();
        }
        
        /**
         * Returns the term at the specified position in the suggestion list.
         * The highest-weighted term is at index 0, the second-highest-weighted
         * term at index 1, and so on.
         */
        public String listElement(int index) {
            return list[index].term;
        }

        /**
         * Returns the number of terms in the suggestion list.
         */
        public int listLength() {
            return list.length;
        }

        private Node topUnlistedTerm() {
            Node topUnlisted = null;
            if(isTerminal()) {
                int i = 0;
                while(i < list.length && list[i] != this)
                    i++;
                if(i == list.length)
                    topUnlisted = this;
            }
            childNodeIteration:
            for(Node child = leftmostChildInTrie();
                    child != null;
                    child = child.rightSiblingInTrie()) {
                int i = 0;
                for(Node terminal : list) {
                    if(terminal == child.list[i]) {
                        i++;
                        if(i == child.list.length)
                            continue childNodeIteration;
                    }
                }
                if(topUnlisted == null
                        || topUnlisted.weight < child.list[i].weight)
                    topUnlisted = child.list[i];
            }
            return topUnlisted;
        }

        private Node leftmostChildInTrie() {
            if(mid == null)
                return null;
            Node n = mid;
            while(n.left != null)
                n = n.left;
            return n;
        }

        private Node rightSiblingInTrie() {
            if(right != null) {
                Node n = right;
                while(n.left != null)
                    n = n.left;
                return n;
            }else{
                Node n = this;
                while(n == n.parent.right)
                    n = n.parent;
                if(n == n.parent.left)
                    return n.parent;
                else
                    return null;
            }
        }
        
        private Node parentInTrie() {
            Node n = this;
            while(n.parent != null && n != n.parent.mid)
                n = n.parent;
            return n.parent;
        }
    }

