MOV AH, 3Ch
MOV DX, "datos.txt"
INT 21H

MOV AH, 40h
MOV AL, 72
MOV DX, "datos.txt"
INT 21H

MOV AH, 4Dh
MOV DX, "datos.txt"
INT 21H

MOV DX, "Archivo leido"
INT 10H

MOV AH, 41h
MOV DX, "datos.txt"
INT 21H

INT 20H