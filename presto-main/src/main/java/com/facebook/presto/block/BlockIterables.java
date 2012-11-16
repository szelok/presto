/*
 * Copyright 2004-present Facebook. All Rights Reserved.
 */
package com.facebook.presto.block;

import com.facebook.presto.block.uncompressed.UncompressedBlock;
import com.facebook.presto.tuple.TupleInfo;
import com.facebook.presto.tuple.TupleInfo.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;

import java.util.Iterator;
import java.util.List;

public final class BlockIterables
{
    private BlockIterables()
    {
    }

    public static BlockIterable createBlockIterable(Block... blocks)
    {
        return new StaticBlockIterable(ImmutableList.copyOf(blocks));
    }

    public static BlockIterable createBlockIterable(Iterable<? extends Block> blocks)
    {
        return new StaticBlockIterable(ImmutableList.copyOf(blocks));
    }

    private static class StaticBlockIterable
            implements BlockIterable
    {
        private final List<Block> blocks;

        public StaticBlockIterable(Iterable<Block> blocks)
        {
            Preconditions.checkNotNull(blocks, "blocks is null");
            this.blocks = ImmutableList.copyOf(blocks);
            Preconditions.checkArgument(!this.blocks.isEmpty(), "blocks is empty");
        }

        @Override
        public TupleInfo getTupleInfo()
        {
            return blocks.get(0).getTupleInfo();
        }

        @Override
        public Iterator<Block> iterator()
        {
            return blocks.iterator();
        }
    }

    public static BlockIterable concat(BlockIterable... blockIterables)
    {
        return new ConcatBlockIterable(ImmutableList.copyOf(blockIterables));
    }

    public static BlockIterable concat(Iterable<? extends BlockIterable> blockIterables)
    {
        return new ConcatBlockIterable(blockIterables);
    }

    private static class ConcatBlockIterable
            implements BlockIterable
    {
        private final Iterable<? extends BlockIterable> blockIterables;
        private final TupleInfo tupleInfo;

        private ConcatBlockIterable(Iterable<? extends BlockIterable> blockIterables)
        {
            this.blockIterables = blockIterables;

            ImmutableList.Builder<Type> types = ImmutableList.builder();
            for (BlockIterable blocks : blockIterables) {
                types.addAll(blocks.getTupleInfo().getTypes());
            }
            tupleInfo = new TupleInfo(types.build());
        }

        @Override
        public TupleInfo getTupleInfo()
        {
            return tupleInfo;
        }

        @Override
        public Iterator<Block> iterator()
        {
            return new AbstractIterator<Block>()
            {
                private final Iterator<? extends BlockIterable> blockIterables = ConcatBlockIterable.this.blockIterables.iterator();
                private Iterator<Block> blocks;

                @Override
                protected Block computeNext()
                {
                    while ((blocks == null || !blocks.hasNext()) && blockIterables.hasNext()) {
                        blocks = blockIterables.next().iterator();
                    }
                    if (blocks == null || !blocks.hasNext()) {
                        return endOfData();
                    }
                    UncompressedBlock block = (UncompressedBlock) blocks.next();
                    return block;
                }
            };
        }
    }
}