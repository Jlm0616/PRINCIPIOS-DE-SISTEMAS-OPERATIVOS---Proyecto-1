INC
LOAD AX
MOV BX, AX
MOV BX, X   ; ← X no es número ni registro
SWAP AX, 5   ; ← 5 no es registro
JMP 3.5   ; ← no es entero
INT 2H   ; ← formato inválido, debe ser 2 dígitos hex + H
PARAM 1, 2, 3, 4   ; ← 4 argumentos, máximo 3
PUSH   ; ← sin argumento
FOO AX   ; ← opcode desconocido