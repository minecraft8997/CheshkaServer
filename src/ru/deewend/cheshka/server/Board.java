package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.packet.DiceRolled;
import ru.deewend.cheshka.server.packet.MakeMove;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Board {
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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
    private final long turnWaitingTimeoutMillis;
    private final int diagonalLength;
    private final int whitesDiagonalStart;
    private final int blacksSpawnPosition;
    private final int blacksDiagonalStartPlusOne;
    private final List<Piece> pieces = new ArrayList<>();
    private int moveNumber = 1;
    private int subMoveNumber = 1;
    private boolean whitesTurn = true;
    private int lastCalculatedDestination;
    private Pair<Integer, List<PossibleMove>> lastDiceRollResult;
    private int noMovesCounter;
    private long lastActionTimestamp = System.currentTimeMillis();
    private byte gameState;
    private boolean lastChance;
    private boolean lastChanceActivated;

    public Board(Random random, int boardSize, long turnWaitingTimeoutMillis) {
        if (boardSize <= 0 || boardSize % 2 != 0) {
            throw new IllegalArgumentException("Bad boardSize");
        }

        this.random = random;
        this.turnWaitingTimeoutMillis = turnWaitingTimeoutMillis;
        diagonalLength = boardSize / 2;
        whitesDiagonalStart = (boardSize - 1) * 4;
        blacksSpawnPosition = whitesDiagonalStart / 2;
        blacksDiagonalStartPlusOne = whitesDiagonalStart + diagonalLength;
    }

    public String serializePosition(boolean white) {
        StringBuilder builder = new StringBuilder();
        for (Piece piece : pieces) {
            if (piece.whitePiece == white) {
                builder.append(piece.position);
                if (piece.revertedPosition && piece.position == blacksSpawnPosition) {
                    builder.append('!');
                }
                builder.append(' ');
            }
        }

        return builder.substring(0, builder.length() - 1); // omitting the last space character
    }

    public DiceRolled rollDice() {
        if (lastDiceRollResult != null) return null;

        int digit = 1 + random.nextInt(6);

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
        lastDiceRollResult = new Pair<>(digit, possibleMoves);
        DiceRolled packet = new DiceRolled();
        packet.value = (byte) digit;

        lastActionTimestamp = System.currentTimeMillis();

        return packet;
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

    public Packet checkTimeout() {
        if (System.currentTimeMillis() - lastActionTimestamp < turnWaitingTimeoutMillis) return null;

        if (lastDiceRollResult == null) return rollDice();

        return makeRandomMove();
    }

    private MakeMove makeRandomMove() {
        List<PossibleMove> possibleMoves = lastDiceRollResult.second();
        if (possibleMoves.isEmpty()) return makeMove(new NoMove(), true);

        for (PossibleMove move : possibleMoves) {
            if (move.isSpawningMove()) return makeMove(move, true);
        }
        int randomIdx = random.nextInt(possibleMoves.size());

        return makeMove(possibleMoves.get(randomIdx), true);
    }

    public MakeMove makeMove(MakeMove packet, boolean white) {
        if (whitesTurn != white) return null;
        if (lastDiceRollResult == null) return null;
        if (packet.moveNumber != moveNumber || packet.subMoveNumber != subMoveNumber) return null;

        List<PossibleMove> possibleMoves = lastDiceRollResult.second();

        switch (packet.moveType) {
            case MakeMove.MOVE_TYPE_GENERAL -> {
                for (PossibleMove move : possibleMoves) {
                    if (move.isSpawningMove()) continue;

                    if (move.piece.position == packet.piecePosition) {
                        return makeMove(move, false);
                    }
                }

                return null;
            }
            case MakeMove.MOVE_TYPE_SPAWNING -> {
                for (PossibleMove move : possibleMoves) {
                    if (move.isSpawningMove() && move.destination == packet.piecePosition) {
                        return makeMove(move, false);
                    }
                }

                return null;
            }
            case MakeMove.MOVE_TYPE_NO_MOVE -> {
                if (!possibleMoves.isEmpty()) return null;

                return makeMove(new NoMove(), false);
            }
            default -> {
                return null;
            }
        }
    }

    @SuppressWarnings("ExtractMethodRecommender")
    private MakeMove makeMove(PossibleMove move, boolean automatic) {
        if (gameState != GAME_STATE_RUNNING) return null;
        int initialPosition = (move.piece != null ? move.piece.position : 0);

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
        packet.piecePosition = initialPosition;
        packet.moveType = move.getMoveType();
        packet.automatic = automatic;

        if (lastDiceRollResult.first() != 6) { // rolling 6 gives you the right to make one more move
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
        lastDiceRollResult = null;
        lastActionTimestamp = System.currentTimeMillis();

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
