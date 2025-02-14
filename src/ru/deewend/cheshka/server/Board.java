package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.packet.MakeMove;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Board {
    public static class Piece {
        private final boolean whitePiece;
        private int position;
        private int movesMade;
        private boolean revertedPosition;

        public Piece(boolean whitePiece) {
            this.whitePiece = whitePiece;
        }

        public boolean isWhitePiece() {
            return whitePiece;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            if (position < this.position) revertedPosition = true;

            this.position = position;
        }

        public int getMovesMade() {
            return movesMade;
        }

        public void setMovesMade(int movesMade) {
            this.movesMade = movesMade;
        }

        public boolean hasRevertedPosition() {
            return revertedPosition;
        }
    }

    public class PossibleMove {
        public static final int NEW_PIECE = -1;

        private final Piece piece;
        private final int destination;

        private PossibleMove(Piece piece, int destination) {
            this.piece = piece;
            this.destination = destination;
        }

        public void makeMove() {
            Piece target = null;
            for (Piece piece : pieces) {
                if (piece.position == destination) {
                    target = piece;

                    break;
                }
            }
            if (target != null) pieces.remove(target);

            if (isSpawningMove()) {
                Piece piece = new Piece(whitesTurn);
                piece.setPosition(getSpawnPosition());

                pieces.add(piece);
            } else {
                piece.setPosition(destination);
            }
        }

        public byte getMoveType() {
            return (isSpawningMove() ? MakeMove.MOVE_TYPE_SPAWNING : MakeMove.MOVE_TYPE_GENERAL);
        }

        public boolean isSpawningMove() {
            return piece == null;
        }

        public Piece getPiece() {
            return piece;
        }

        public int getDestination() {
            return destination;
        }
    }

    public class NoMove extends PossibleMove {
        private NoMove() {
            super(null, 0);
        }

        @Override
        public void makeMove() {
            // do nothing
        }

        public byte getMoveType() {
            return MakeMove.MOVE_TYPE_NO_MOVE;
        }
    }

    public static final int NO_MOVE_DRAW_THRESHOLD =
            Integer.parseInt(Helper.getProperty("noMoveDrawThreshold", "24"));
    public static final byte GAME_STATE_RUNNING = 0;
    public static final byte GAME_STATE_WHITE_WON = 1;
    public static final byte GAME_STATE_BLACK_WON = 2;
    public static final byte GAME_STATE_DRAW = 3;

    private final Random random;
    private final int boardSize;
    private final int diagonalLength;
    private final int whitesDiagonalStart;
    private final int blacksSpawnPosition;
    private final int blacksDiagonalStartPlusOne;
    private final List<Piece> pieces = new ArrayList<>();
    private int moveNumber = 1;
    private int subMoveNumber = 1;
    private boolean whitesTurn = true;
    private int lastDiceRollResult;
    private int lastCalculatedDestination;
    private int noMovesCounter;
    private byte gameState;
    private boolean lastChance;
    private boolean lastChanceActivated;

    public Board(Random random, int boardSize) {
        if (boardSize <= 0 || boardSize % 2 != 0) {
            throw new IllegalArgumentException("Bad boardSize");
        }

        this.random = random;
        this.boardSize = boardSize;
        diagonalLength = boardSize / 2;
        whitesDiagonalStart = (boardSize - 1) * 4;
        blacksSpawnPosition = whitesDiagonalStart / 2;
        blacksDiagonalStartPlusOne = whitesDiagonalStart + diagonalLength;
    }

    public String serializePosition(boolean white) {
        return "";
    }

    public Pair<Integer, List<PossibleMove>> rollDice() {
        int digit = 1 + random.nextInt(6);
        lastDiceRollResult = digit;

        List<PossibleMove> possibleMoves = new ArrayList<>();
        if (isMovePossible(null, digit)) {
            possibleMoves.add(new PossibleMove(null, getSpawnPosition()));
        }
        for (Piece piece : pieces) {
            if (piece.whitePiece != whitesTurn) continue;

            if (isMovePossible(piece, digit)) {
                possibleMoves.add(new PossibleMove(piece, lastCalculatedDestination));
            }
        }

        return new Pair<>(digit, possibleMoves);
    }

    /*
     * Modifies lastCalculatedDestination only if piece != null.
     */
    private boolean isMovePossible(Piece piece, int digit) {
        if (piece == null) {
            if (digit != 6) return false;

            int pieceCount = 0;
            for (Piece aPiece : pieces) {
                if (aPiece.whitePiece == whitesTurn) {
                    pieceCount++;

                    if (pieceCount >= diagonalLength) return false;
                }
            }

            for (Piece aPiece : pieces) {
                if (aPiece.position == getSpawnPosition() || (whitesTurn && aPiece.position == whitesDiagonalStart)) {
                    /*
                     * If aPiece.position == whitesDiagonalStart, then it is
                     * guaranteed that aPiece.whitePiece is true. Maybe simplify that somehow?
                     */

                    return aPiece.whitePiece != whitesTurn;
                }
            }

            return true;
        }

        int oldPosition = piece.position;
        for (int i = 1; i <= digit; i++) {
            int newPosition;
            if (oldPosition == whitesDiagonalStart - 1 && !whitesTurn) {
                newPosition = 0;
            } else if (!whitesTurn && oldPosition == getSpawnPosition() && piece.revertedPosition) {
                newPosition = blacksDiagonalStartPlusOne;
            } else if (whitesTurn && oldPosition == whitesDiagonalStart + diagonalLength - 1) {
                return false;
            } else if (!whitesTurn && oldPosition == blacksDiagonalStartPlusOne + diagonalLength - 2) {
                return false;
            } else {
                newPosition = oldPosition + 1;
            }
            oldPosition = newPosition;
            lastCalculatedDestination = newPosition;

            for (Piece aPiece : pieces) {
                if (aPiece.position == newPosition || (newPosition == whitesDiagonalStart && aPiece.position == 0)) {
                    if (i != digit) return false;

                    return aPiece.whitePiece != whitesTurn;
                }
            }
        }

        return true;
    }

    private int getSpawnPosition() {
        return (whitesTurn ? 0 : blacksSpawnPosition);
    }

    @SuppressWarnings("ExtractMethodRecommender")
    public MakeMove makeMove(PossibleMove move) {
        if (gameState != GAME_STATE_RUNNING) {
            throw new IllegalStateException("Attempted to make a move in a finished game");
        }

        move.makeMove();

        if (move instanceof NoMove) {
            noMovesCounter++;
            if (noMovesCounter >= NO_MOVE_DRAW_THRESHOLD) {
                gameState = GAME_STATE_DRAW;
            }
        } else {
            noMovesCounter = 0;

            boolean whiteFinished;
            {
                boolean ok = true;
                for (int i = whitesDiagonalStart; i < whitesDiagonalStart + diagonalLength; i++) {
                    if (!checkPiece(i, true, false)) {
                        ok = false;

                        break;
                    }
                }

                whiteFinished = ok;
            }
            boolean blackFinished;
            {
                if (!checkPiece(blacksSpawnPosition, false, true)) {
                    blackFinished = false;
                } else {
                    boolean ok = true;
                    for (int i = blacksDiagonalStartPlusOne; i < blacksDiagonalStartPlusOne + diagonalLength - 1; i++) {
                        if (!checkPiece(i, false, false /* does not really matter, could be true */)) {
                            ok = false;

                            break;
                        }
                    }

                    blackFinished = ok;
                }
            }
            if (lastChanceActivated) {
                if (whiteFinished && blackFinished) {
                    gameState = GAME_STATE_DRAW;

                    lastChance = false;
                }
                if ((whitesTurn && !blackFinished) || (!whitesTurn && !whiteFinished)) {
                    lastChance = false;
                    lastChanceActivated = false; // continue the game
                }
            } else if (whiteFinished || blackFinished) {
                lastChance = true;
            }
        }
        MakeMove packet = new MakeMove();
        packet.moveNumber = moveNumber;
        packet.subMoveNumber = subMoveNumber++;
        packet.moveType = move.getMoveType();

        if (lastDiceRollResult != 6) { // rolling 6 gives you the right to make one more move
            if (!whitesTurn) {
                moveNumber++;
                subMoveNumber = 1;
            }

            whitesTurn = !whitesTurn;

            if (lastChance) {
                if (!lastChanceActivated) {
                    lastChanceActivated = true;
                } else {
                    if (whitesTurn) gameState = GAME_STATE_WHITE_WON;
                    else            gameState = GAME_STATE_BLACK_WON;
                }
            }
        }

        return packet;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkPiece(int position, boolean whiteRequired, boolean mustBeReverted) {
        Piece found = null;
        for (Piece piece : pieces) {
            if (piece.position != position) continue;
            if (mustBeReverted && !piece.revertedPosition) continue;

            found = piece;

            break;
        }
        if (found == null) return false;

        return found.whitePiece == whiteRequired;
    }

    public byte getGameState() {
        return gameState;
    }
}
