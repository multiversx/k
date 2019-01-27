package main

import (
	"fmt"
	koreparser "$INCLUDE_KORE_PARSER$"
	"log"
	"os"
	"os/exec"
)

func callKast(programPath string) []byte {
	cmd := exec.Command("/home/andrei/elrond/k/k-distribution/target/release/k/bin/kast", programPath)
	cmd.Dir = "../"
	out, err := cmd.Output()
	if err != nil {
		log.Fatal("Kast error: " + err.Error())
	}
	return out
}

func kastParseAndPrint() {
	testArg := os.Args[1]
	kast := callKast("tests/" + testArg)
	fmt.Printf("Kast: %s\n\n", kast)

	parserK := koreparser.Parse(kast)
	kinput := convertParserModelToKModel(parserK)
	fmt.Println("input:")
	fmt.Println(kinput.PrettyTreePrint(0))

	// top cell initialization
	m := make(map[K]K)
	m[KToken{Sort: sortKConfigVar, Value: "$PGM"}] = kinput
	kmap := Map{Sort: sortMap, Label: klabelForMap, data: m}
	evalK := KApply{Label: topCellInitializer, List: []K{kmap}}
	kinit, err := eval(evalK, Bottom{})
	if err != nil {
		fmt.Println(err.Error())
		return
	}
	fmt.Println("\n\ntop level init:")
	fmt.Println(kinit.PrettyTreePrint(0))

	// execute
	final, stepsMade := takeStepsNoThread(kinit, 100)
	fmt.Println("\n\nresult:")
	fmt.Println(final.PrettyTreePrint(0))

	fmt.Printf("\n\nsteps made: %d\n", stepsMade)

}

func takeStepsNoThread(k K, maxSteps int) (K, int) {
	n := 0
	current := k
	var err error
	for n < maxSteps {
		current, err = step(current)
		if err != nil {
			if _, t := err.(*noStepError); t {
				return current, n
			}
			panic(err.Error())
		}
		n++
	}
	return current, n
}

func main() {
	kastParseAndPrint()

}
