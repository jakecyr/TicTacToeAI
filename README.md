# TicTacToeAI

The TicTacToeAI class has two constructor paramters. 

The first parameter specifies which file to save and load the "brain" from. 
If the file doesn't exist, one will be created and written to. The data files are stored in the "data/" folder.

The second parameter specifies the type of AI:
1 = Smart
2 = Random

The Smart AI type will try to find an optimal move each turn.
If it can't find an optimal move in its long-term memory, it will resort to picking a random empty spot.

The heat value in TicTacToeAI class specifies the degree of randomness for the "Smart" AI to play at. 
The value is incremented by 1 after every 100 games.

A heat max can also be specified to ensure that the AI explores a random positon every once in a while.
