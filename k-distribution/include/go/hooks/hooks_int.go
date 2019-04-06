package %PACKAGE_INTERPRETER%

import (
	m "%INCLUDE_MODEL%"
	"math/big"
)

type intHooksType int

const intHooks intHooksType = 0

func (intHooksType) eq(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	return m.ToBool(i1.Value.Cmp(i2.Value) == 0), nil
}

func (intHooksType) ne(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	return m.ToBool(i1.Value.Cmp(i2.Value) != 0), nil
}

func (intHooksType) le(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	return m.ToBool(i1.Value.Cmp(i2.Value) <= 0), nil
}

func (intHooksType) lt(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	return m.ToBool(i1.Value.Cmp(i2.Value) < 0), nil
}

func (intHooksType) ge(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	return m.ToBool(i1.Value.Cmp(i2.Value) >= 0), nil
}

func (intHooksType) gt(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	return m.ToBool(i1.Value.Cmp(i2.Value) > 0), nil
}

func (intHooksType) add(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Add(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) sub(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Sub(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) mul(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Mul(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (t intHooksType) tdiv(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	// TODO: investigate if we need another implementation here
	return t.ediv(c1, c2, lbl, sort, config)
}

func (t intHooksType) tmod(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	// TODO: investigate if we need another implementation here
	return t.emod(c1, c2, lbl, sort, config)
}

// euclidian division
func (intHooksType) ediv(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	if i2.Value.Cmp(m.IntZero.Value) == 0 {
		return m.NoResult, &hookDivisionByZeroError{}
	}
	var z big.Int
	z.Div(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) emod(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	if i2.Value.Cmp(m.IntZero.Value) == 0 {
		return m.NoResult, &hookDivisionByZeroError{}
	}
	var z big.Int
	z.Mod(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) pow(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Exp(i1.Value, i2.Value, nil)
	return m.NewInt(&z), nil
}

func (intHooksType) powmod(c1 m.K, c2 m.K, c3 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	i3, ok3 := c3.(*m.Int)
	if !ok1 || !ok2 || !ok3 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Exp(i1.Value, i2.Value, i3.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) shl(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	if !i2.Value.IsUint64() {
		return invalidArgsResult()
	}
	var z big.Int
	z.Lsh(i1.Value, uint(i2.Value.Uint64()))
	return m.NewInt(&z), nil
}

func (intHooksType) shr(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	if !i2.Value.IsUint64() {
		return invalidArgsResult()
	}
	var z big.Int
	z.Rsh(i1.Value, uint(i2.Value.Uint64()))
	return m.NewInt(&z), nil
}

func (intHooksType) and(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.And(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) or(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Or(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) xor(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	var z big.Int
	z.Xor(i1.Value, i2.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) not(c m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i, ok := c.(*m.Int)
	if !ok {
		return invalidArgsResult()
	}
	var z big.Int
	z.Not(i.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) abs(c m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i, ok := c.(*m.Int)
	if !ok {
		return invalidArgsResult()
	}
	var z big.Int
	z.Abs(i.Value)
	return m.NewInt(&z), nil
}

func (intHooksType) max(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	if i1.Value.Cmp(i2.Value) >= 0 {
		return c1, nil
	}
	return c2, nil
}

func (intHooksType) min(c1 m.K, c2 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	i1, ok1 := c1.(*m.Int)
	i2, ok2 := c2.(*m.Int)
	if !ok1 || !ok2 {
		return invalidArgsResult()
	}
	if i1.Value.Cmp(i2.Value) >= 0 {
		return c2, nil
	}
	return c1, nil
}

func (intHooksType) log2(karg m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	bigi, ok := karg.(*m.Int)
	if !ok {
		return invalidArgsResult()
	}
	if bigi.Value.Sign() <= 0 {
		return invalidArgsResult()
	}
	bytes := bigi.Value.Bytes()
	leadingByte := bytes[0]
	nrBytes := 0
	for leadingByte > 0 {
		leadingByte = leadingByte >> 1
		nrBytes++
	}
	return m.NewIntFromInt(nrBytes + (len(bytes)-1)*8 - 1), nil
}

func (intHooksType) bitRange(argI m.K, argOffset m.K, argLen m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	ki, ok1 := argI.(*m.Int)
	koff, ok2 := argOffset.(*m.Int)
	klen, ok3 := argLen.(*m.Int)
	if !ok1 || !ok2 || !ok3 {
		return invalidArgsResult()
	}
	if ki.IsZero() {
		return m.IntZero, nil // any operation on zero will result in zero
	}

	offset, offsetOk := koff.ToPositiveInt32()
	length, lengthOk := klen.ToPositiveInt32()
	if !offsetOk || !lengthOk {
		return invalidArgsResult()
	}
	if length == 0 {
		return m.IntZero, nil
	}
	if offset&7 != 0 || length&7 != 0 {
		// this is a quick check that they are both divisible by 8
		// as long as they are divisible by 8, we can operate on whole bytes
		// if they are not, things get more complicated, will only implement when necessary
		return m.NoResult, &hookNotImplementedError{}
	}
	offsetBytes := offset >> 3 // divide by 8 to get number of bytes
	lengthBytes := length >> 3 // divide by 8 to get number of bytes

	resBytes := ki.ToTwosComplementBytes(lengthBytes + offsetBytes)
	if offsetBytes != 0 {
		resBytes = resBytes[0:lengthBytes]
	}

	result := new(big.Int)
	result.SetBytes(resBytes)
	return &m.Int{Value: result}, nil
}

func (intHooksType) signExtendBitRange(c1 m.K, c2 m.K, c3 m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	return m.NoResult, &hookNotImplementedError{}
}

func (intHooksType) rand(c m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	return m.NoResult, &hookNotImplementedError{}
}

func (intHooksType) srand(c m.K, lbl m.KLabel, sort m.Sort, config m.K) (m.K, error) {
	return m.NoResult, &hookNotImplementedError{}
}
